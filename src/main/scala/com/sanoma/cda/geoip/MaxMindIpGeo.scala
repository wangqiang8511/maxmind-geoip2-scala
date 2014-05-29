/*
 * Copyright (c) 2013-2014 Sanoma Oyj. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.sanoma.cda.geoip

import com.sanoma.cda.geo._

// Import MaxMind
import com.maxmind.geoip2.DatabaseReader

// Import LRU map from Twitter
import com.twitter.util.{LruMap, SynchronizedLruMap}

// Java for MaxMind
import java.io.File
import java.net.InetAddress


/**
 * This is a simple Scala wrapper around the MaxMind Java geo lookup class:
 * https://github.com/maxmind/GeoIP2-java
 * I.e. it uses the new Beta API.
 * Download the database from http://geolite.maxmind.com/download/geoip/database/GeoLite2-City.mmdb.gz
 *
 * Inspired by https://github.com/snowplow/scala-maxmind-geoip
 * @param dbFile The DB file unzipped
 * @param lruCache The Size of the LRU cache
 */
class MaxMindIpGeo(dbFile: File, lruCache: Int = 10000, synchronized: Boolean = false, geoPointBlacklist: Set[Point] = Set()) {

  /**
   * Helper function that turns string into InetAddress
   * @param address The address for lookup. If it looks like IP address, will use it as such. If not, then it will use it as name.
   * @return Option[InetAddress]
   */
  def getInetAddress(address: String) = {
    // Some people, when confronted with a problem, think "I know, I'll use regular expressions." Now they have two problems.
    val validNum = """(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])"""
    val dot = """\."""
    val validIP = (validNum + dot + validNum + dot + validNum + dot + validNum).r

    try {
      address match {
        case validIP(_, _, _, _) => Some(InetAddress.getByAddress(address.split('.').map(_.toInt.toByte)))
        case _                   => Some(InetAddress.getByName(address))
      }
    } catch { // if all fails...
      case _ : Throwable => None
    }
  }


  // build the lookup service
  private val maxmind = new DatabaseReader.Builder(dbFile).build

  /**
   * Reads the address location from location service DB
   * @param address
   * @return Option[com.maxmind.geoip2.model.OmniResponse]
   */
  def getLocationFromDB(address: String) = try {
    getInetAddress(address).map(maxmind.omni(_))
  } catch {
    case _ : Throwable => None // we don't really care about which exception we got
  }


  // setup cache
  def chooseAndCreateNewLru = if (synchronized) new SynchronizedLruMap[String, Option[IpLocation]](lruCache)
                              else new LruMap[String, Option[IpLocation]](lruCache)

  private val lru = if (lruCache > 0) chooseAndCreateNewLru else null

  // There seem to be lot of IP addresses that give back a location that is definitely wrong
  // TODO: extend this as if the lat, long is wrong, then other things are probably wrong too
  def filterBlacklistedCoordinates(loc: IpLocation) = loc.geoPoint match {
    case Some(p) if geoPointBlacklist.contains(p) => loc.copy(geoPoint = None)
    case _ => loc
  }

  // define the actual accessor methods
  def getLocationFiltered(address: String) = getLocationFromDB(address).map(IpLocation(_)).map(filterBlacklistedCoordinates)
  def getLocationUnfiltered(address: String) = getLocationFromDB(address).map(IpLocation(_))

  /**
   * Returns the location of given address directly from DB
   * @param address The IP or host
   * @return Option[IpLocation]
   */
  val getLocationWithoutLruCache = if (geoPointBlacklist.isEmpty) getLocationUnfiltered _ else getLocationFiltered _


  /**
   * Returns the location of given address from LRU or from DB
   * @param address The IP or host
   * @return Option[IpLocation]
   */
  def getLocationWithLruCache(address: String) = {
    lru.get(address) match {
      case Some(loc) => loc
      case None => {
        val loc = getLocationWithoutLruCache(address)
        lru.put(address, loc)
        loc
      }
    }
  }

  // finally the method that you are looking for
  /**
   * This is the main method that returns the Option[IpLocation] form given IP or host
   * @return The method that provides the IpLocation from given input string representing either IP address or name
   */
  val getLocation: String => Option[IpLocation] = if (lruCache > 0) getLocationWithLruCache else getLocationWithoutLruCache

}



/**
 * Companion object to generate new MaxMindIpGeo instance
 */
object MaxMindIpGeo {
  /**
   * Alternative constructor, probably the one you are going to use
   */
  def apply(dbFile: String, lruCache: Int = 10000, synchronized: Boolean = false, geoPointBlacklist: Set[Point] = Set()) = {
    new MaxMindIpGeo(new File(dbFile), lruCache, synchronized, geoPointBlacklist)
  }

}
