/**
 *  Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.cluster

import akka.testkit.AkkaSpec

class SerializeCreatorsVerificationSpec extends AkkaSpec {

  "serialize-creators should be on" in {
    system.settings.SerializeAllCreators should equal(true)
  }

}