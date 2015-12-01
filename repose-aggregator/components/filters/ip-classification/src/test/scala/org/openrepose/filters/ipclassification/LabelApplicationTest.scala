package org.openrepose.filters.ipclassification

import org.junit.runner.RunWith
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LabelApplicationTest extends FunSpec with Matchers {
  describe("A configured filter with a catch all ipv4 rule") {
    val validConfig =
      """<?xml version="1.0" encoding="UTF-8"?>
        |<ip-classification xmlns="http://docs.openrepose.org/repose/ip-classification/v1.0">
        |    <classifications>
        |        <classification label="sample-group">
        |            <cidr-ip>192.168.1.0/24</cidr-ip>
        |            <cidr-ip>192.168.0.1/32</cidr-ip>
        |        </classification>
        |        <classification label="sample-ipv6-group">
        |            <cidr-ip>2001:db8::/48</cidr-ip>
        |        </classification>
        |        <classification label="bolth-group">
        |            <cidr-ip>10.10.220.0/24</cidr-ip>
        |            <cidr-ip>2001:1938:80:bc::1/64</cidr-ip>
        |        </classification>
        |        <classification label="ipv4-match-all">
        |            <cidr-ip>0.0.0.0/0</cidr-ip>
        |        </classification>
        |    </classifications>
        |</ip-classification>
      """.stripMargin
    val filter = new IPClassificationFilter(null) //Not going to use the config Service
    filter.configurationUpdated(Marshaller.configFromString(validConfig))

    it("returns the correct label for an IPv4 address in 10.10.220.0/24") {
      filter.getClassificationLabel("10.10.220.101") should equal(Some("bolth-group"))
    }
    it("returns the correct label for an IPv4 address in 192.168.1.0/24") {
      filter.getClassificationLabel("192.168.1.1") should equal(Some("sample-group"))
    }
    it("returns the correct label for an IPv4 address in 192.168.0.1/32") {
      filter.getClassificationLabel("192.168.0.1") should equal(Some("sample-group"))
    }
    it("returns the correct label for an IPv6 address in 2001:1938:80:bc::1/64") {
      filter.getClassificationLabel("2001:1938:80:bc::DEAD:BEEF") should equal(Some("bolth-group"))
    }
    it("returns the correct label for the catch all IPv4 entry") {
      filter.getClassificationLabel("8.8.8.8") should equal(Some("ipv4-match-all"))
    }
    it("Will not return a catch all for an IPv6 address") {
      filter.getClassificationLabel("2002::1") should equal(None)
    }
  }
  describe("A configured filter with an ipv6 catch all rule") {
    val validConfig =
      """<?xml version="1.0" encoding="UTF-8"?>
        |<ip-classification xmlns="http://docs.openrepose.org/repose/ip-classification/v1.0">
        |    <classifications>
        |        <classification label="ipv6-match-all">
        |            <cidr-ip>0::0/0</cidr-ip>
        |        </classification>
        |    </classifications>
        |</ip-classification>
      """.stripMargin
    val filter = new IPClassificationFilter(null) //Not going to use the config Service
    filter.configurationUpdated(Marshaller.configFromString(validConfig))

    it("returns the IpV6 catch all, since the servlet filter doesn't know that it's IPv6 or IPv4") {
      filter.getClassificationLabel("8.8.8.8") should equal(Some("ipv6-match-all"))
    }
    it("returns the ipv6 catchall label") {
      filter.getClassificationLabel("2002::1") should equal(Some("ipv6-match-all"))
    }
  }
}
