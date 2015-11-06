# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.


# this script will cover VMdeployment  with Userdata tests

from marvin.lib.base import *
from marvin.lib.utils import *
from marvin.lib.common import *
from nose.plugins.attrib import attr

from nuageTestCase import nuageTestCase

class TestDeployVm(nuageTestCase):
    """Tests for UserData
    """

    @classmethod
    def setUpClass(cls):
        super(TestDeployVm,  cls).setUpClass()
        return

    def setUp(self):
        self.account = Account.create(
            self.apiclient,
            self.services["account"],
            admin=True,
            domainid=self.domain.id
        )
        self.cleanup = [self.account]

        return

    @attr(tags=["nuage"])
    def test_deployvm_vr_ip(self):
        """Test userdata as POST, size > 2k
        """

        self.network = self.create_Network(self.services["nuage_network_offerings"]["isolated_no_vr"], '10.8.1.1')

        with self.assertRaises(CloudstackAPIException):
            deployVmResponse = VirtualMachine.create(
                self.apiclient,
                services=self.services["virtual_machine"],
                accountid=self.account.name,
                domainid=self.account.domainid,
                serviceofferingid=self.service_offering.id,
                networkids=[str(self.network.id)],
                ipaddress="10.8.1.2"
            )
