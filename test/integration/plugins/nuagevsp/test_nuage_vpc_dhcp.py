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

""" Component tests for basic VPC Network functionality with
Nuage VSP SDN plugin
"""
# Import Local Modules
from nuageTestCase import nuageTestCase
from marvin.lib.base import (Account,
                             Domain,
                             Network,
                             VirtualMachine,
                             Configurations,
                             NetworkOffering)
from marvin.cloudstackAPI import updateVirtualMachine, updateZone
from marvin.lib.common import list_virtual_machines
from marvin.codes import PASS
from marvin.lib.utils import cleanup_resources, validateList
# Import System Modules
from nose.plugins.attrib import attr


class TestNuageVpcNetwork(nuageTestCase):
    """ Test basic VPC Network functionality with Nuage VSP SDN plugin
    """

    @classmethod
    def setUpClass(cls, zone=None):
        super(TestNuageVpcNetwork, cls).setUpClass()
        cls.vmdata = cls.test_data["virtual_machine"]
        cls.domains_accounts_data = cls.test_data["acl"]
        cls.cleanup_domain_templates = []

        new_domain_template = cls.vsdk.NUDomainTemplate(
            name="domain_template",
            description="domain_template")
        enterprise = cls._session.user.enterprises.get_first(
            filter="externalID BEGINSWITH '%s'" % cls.domain.id)
        enterprise.create_child(new_domain_template)
        cls.cleanup_domain_templates.append(
            enterprise.domain_templates.get_first(
                filter="name is 'domain_template'"))
        return

    @classmethod
    def tearDownClass(cls):
        # Cleanup resources used
        cls.debug("Cleaning up the resources")
        for obj in reversed(cls._cleanup):
            try:
                if isinstance(obj, VirtualMachine):
                    obj.delete(cls.api_client, expunge=True)
                else:
                    obj.delete(cls.api_client)
            except Exception as e:
                cls.error("Failed to cleanup %s, got %s" % (obj, e))
        for domain_template in cls.cleanup_domain_templates:
            try:
                domain_template.delete()
            except Exception as e:
                cls.error("Failed to cleanup domain template %s in VSD, got "
                              "%s" % (domain_template, e))
        cls.cleanup_domain_templates = []
        # cleanup_resources(cls.api_client, cls._cleanup)
        cls._cleanup = []
        cls.debug("Cleanup complete!")
        return

    def setUp(self):
        # Create an account
        self.account = Account.create(self.api_client,
                                      self.test_data["account"],
                                      admin=True,
                                      domainid=self.domain.id
                                      )
        self.vmdata["details"] = {"dhcp:114": "http://www.testdhcpfeature.com/adfsgbfgtdhh125ki-23-fdh-09"}
        self.vmdata["displayname"] = "vm"
        self.vmdata["name"] = "vm"
        self.update_NuageVspGlobalDomainTemplateName(name="")

        self.cleanup = [self.account]
        return

    def tearDown(self):
        # Cleanup resources used
        self.debug("Cleaning up the resources")
        self.update_NuageVspGlobalDomainTemplateName(name="")
        for obj in reversed(self.cleanup):
            try:
                if isinstance(obj, VirtualMachine):
                    obj.delete(self.api_client, expunge=True)
                else:
                    obj.delete(self.api_client)
            except Exception as e:
                self.error("Failed to cleanup %s, got %s" % (obj, e))
        # cleanup_resources(self.api_client, self.cleanup)
        self.cleanup = []
        self.debug("Cleanup complete!")
        return

    def verify_vsd_dhcp_option_subnet(self, dhcp_type, value, subnet):
        self.debug("Verifying the creation and value of DHCP option type - %s in VSD" % dhcp_type)
        found_dhcp_type = False
        dhcp_options = self.vsd.get_subnet_dhcpoptions(filter=self.get_externalID_filter(subnet.id))
        for dhcp_option in dhcp_options:
            self.debug("dhcptype option in vsd is : %s" % dhcp_option.actual_type)
            self.debug("dhcptype expected value is: %s" % value)
            if dhcp_option.actual_type == dhcp_type:
                found_dhcp_type = True
                if isinstance(dhcp_option.actual_values, list):
                    self.debug("dhcptype actual value on vsd is %s:" % dhcp_option.actual_values)
                    if value in dhcp_option.actual_values:
                        self.debug("Excepted DHCP option value found in VSD")
                    else:
                        self.fail("Excepted DHCP option value not found in VSD")
                else:
                    self.debug("dhcptype actual value on vsd is %s:" % dhcp_option.actual_values)
                    self.assertEqual(dhcp_option.actual_values, value,
                                     "Expected DHCP option value is not same in both CloudStack and VSD"
                    )
        if not found_dhcp_type:
            self.fail("Expected DHCP option type and value not found in the VSD")
        self.debug("Successfully verified the creation and value of DHCP option type - %s in VSD" % dhcp_type)

    def verify_vsd_dhcp_option(self, dhcp_type, value, vm_interface):
        self.debug("Verifying the creation and value of DHCP option type - %s in VSD" % dhcp_type)
        self.debug("Expected value for this dhcp option is - %s in VSD" % value)
        found_dhcp_type = False
        dhcp_options = self.vsd.get_vm_interface_dhcpoptions(
            filter=self.get_externalID_filter(vm_interface.id))
        for dhcp_option in dhcp_options:
            self.debug("dhcptype on vsd is %s:" % dhcp_option.actual_type)
            self.debug("dhcp value on vsd is: %s:" % dhcp_option.actual_values)
            if dhcp_option.actual_type == dhcp_type:
                found_dhcp_type = True
                if isinstance(dhcp_option.actual_values, list):
                    self.debug("dhcptype actual value is %s:" % dhcp_option.actual_values)
                    if value in dhcp_option.actual_values:
                        self.debug("Excepted DHCP option value found in VSD")
                    else:
                        self.fail("Excepted DHCP option value not found in VSD")
                else:
                    self.assertEqual(dhcp_option.actual_values, value,
                                     "Expected DHCP option value is not same in both CloudStack and VSD"
                    )
        if not found_dhcp_type:
            self.fail("Expected DHCP option type and value not found in the VSD for dhcp type %s " % dhcp_type)
        self.debug("Successfully verified the creation and value of DHCP option type - %s in VSD" % dhcp_type)


    def verify_vsd_dhcp_option_empty(self, dhcp_type, vm_interface):
        self.debug("Verifying the creation and value of DHCP option type - %s in VSD" % dhcp_type)
        self.debug("Expected value is empty string")
        dhcp_options = self.vsd.get_vm_interface_dhcpoptions(
            filter=self.get_externalID_filter(vm_interface.id))
        for dhcp_option in dhcp_options:
            self.debug("dhcptype on vsd is %s:" % dhcp_option.actual_type)
            self.debug("dhcp value on vsd is: %s:" % dhcp_option.value)
            if dhcp_option.actual_type == dhcp_type:
                self.assertEqual(dhcp_option.value, "00",
                                 "Expected DHCP option value is not same in both CloudStack and VSD"
                )
        self.debug("Successfully verified the creation and value of DHCP option type - %s in VSD" % dhcp_type)


    def verify_vsd_dhcp_value_notpresent(self, value, vm_interface):
        self.debug("Verifying that on vminterface value is not present- %s" % value)
        dhcp_options = self.vsd.get_vm_interface_dhcpoptions(
            filter=self.get_externalID_filter(vm_interface.id))
        for dhcp_option in dhcp_options:
            self.debug("dhcptype option is %s:" % dhcp_option.actual_type)
            if isinstance(dhcp_option.actual_values, list):
                self.debug("dhcptype actual value is %s:" % dhcp_option.actual_values)
                if value in dhcp_option.actual_values:
                    self.fail("This value is not expected on vminterface but present as dhcp_type %s"
                              % dhcp_option.actual_type)
                else:
                    self.debug("As Excepted DHCP value not found in VSD")
            else:
                try:
                    self.assertEqual(dhcp_option.actual_values, value,
                                     "Expected DHCP option value is not same in both CloudStack and VSD"
                    )
                    self.fail("This value is not expected on vminterface but present as dhcp_type %s"
                              % dhcp_option.actual_type)
                except Exception as e:
                    self.debug("As Expected DHCP value not found in VSD")
        self.debug("Successfully verified dhcp value is not present - %s in VSD" % value)

    def verify_vsd_dhcp_type_notpresent(self, dhcp_type, vm_interface):
        self.debug("Verifying that DHCP option type - %s not present in VSD" % dhcp_type)
        dhcp_options = self.vsd.get_vm_interface_dhcpoptions(
            filter=self.get_externalID_filter(vm_interface.id))
        for dhcp_option in dhcp_options:
            self.debug("dhcptype on vsd is %s:" % dhcp_option.actual_type)
            if dhcp_option.actual_type == dhcp_type:
                self.fail("Expected DHCP option type is not expected in the VSD: %s" % dhcp_type)
        self.debug("Successfully verified DHCP option type - %s not present in VSD" % dhcp_type)

    def verify_dhcp_on_vm(self, dhcpleasefile, value, ssh_client, cleanlease=True):
        cmd = 'cat /var/lib/dhcp/'+dhcpleasefile
        self.debug("get content of dhcp lease file " + cmd)
        outputlist = ssh_client.execute(cmd)
        self.debug("command is executed properly " + cmd)
        completeoutput = str(outputlist).strip('[]')
        self.debug("complete output is " + completeoutput)
        if value in completeoutput:
            self.debug("excepted value found in vm: " + value)
        else:
            self.fail("excepted value not found in vm: " + value)
        if cleanlease:
            cmd = 'rm -rf /var/lib/dhcp/'+dhcpleasefile
            outputlist = ssh_client.execute(cmd)
            completeoutput = str(outputlist).strip('[]')
            self.debug("clear lease is done properly:" + completeoutput)

    def update_vm_details(self, vm, updatedetail):
        """Updates the VM data"""

        cmd = updateVirtualMachine.updateVirtualMachineCmd()
        cmd.id = vm.id
        cmd.details = [{}]
        cmd.details[0] = updatedetail
        self.api_client.updateVirtualMachine(cmd)

    def update_zone_details(self, value):
        """Updates the VM data"""

        # update Network Domain at zone level
        cmd = updateZone.updateZoneCmd()
        cmd.id = self.zone.id
        cmd.domain = value
        self.api_client.updateZone(cmd)

    def update_NuageVspGlobalDomainTemplateName(self, name):
        self.debug("Updating global setting nuagevsp.vpc.domaintemplate.name "
                   "with value - %s" % name)
        Configurations.update(self.api_client,
                              name="nuagevsp.vpc.domaintemplate.name",
                              value=name)
        self.debug("Successfully updated global setting "
                   "nuagevsp.vpc.domaintemplate.name with value - %s" % name)
        domain_template_name = Configurations.list(
            self.api_client,
            name="nuagevsp.vpc.domaintemplate.name")[0].value

    @attr(tags=["advanced", "nuagevsp"], required_hardware="false")
    def test_01_nuage_vpc_network(self):
        """ Test basic VPC Network functionality with DHCP15/12/114 VSP SDN plugin
        """

        # 1. Create Nuage VSP VPC offering, check if it is successfully
        #    created and enabled.
        # 2. Create a VPC with Nuage VSP VPC offering, check if it is
        #    successfully created and enabled.
        # 3. Create Nuage VSP VPC Network offering, check if it is successfully
        #    created and enabled.
        # 4. Create an ACL list in the created VPC, and add an ACL item to it.
        # 5. Create a VPC Network with Nuage VSP VPC Network offering and the
        #    created ACL list, check if it is successfully created, is in the
        #    "Implemented" state, and is added to the VPC VR.
        # 6. Deploy a VM in the created VPC network, check if the VM is
        #    successfully deployed and is in the "Running" state.
        # 7. Verify that the created ACL item is successfully implemented in
        #    Nuage VSP.
        # 8. Delete all the created objects (cleanup).

        # Creating a VPC offering
        self.debug("Creating Nuage VSP VPC offering without dhcp...")
        vpc_offering = self.create_VpcOffering(
            self.test_data["nuagevsp"]["vpc_offering_nodhcp"])
        self.validate_VpcOffering(vpc_offering, state="Enabled")

        # Creating a VPC
        self.debug("Creating a VPC with Nuage VSP VPC offering...")
        vpc = self.create_Vpc(vpc_offering, cidr='10.1.0.0/16', networkDomain="testvpc.com")
        self.validate_Vpc(vpc, state="Enabled")

        # Creating a network offering
        self.debug("Creating Nuage VSP VPC Network offering without dhcp...")
        network_offering = self.create_NetworkOffering(
            self.test_data["nuagevsp"]["vpc_network_offering_nodhcp"])
        self.validate_NetworkOffering(network_offering, state="Enabled")

        # Creating an ACL list
        acl_list = self.create_NetworkAclList(
            name="acl", description="acl", vpc=vpc)

        # Creating an ACL item
        acl_item = self.create_NetworkAclRule(
            self.test_data["ingress_rule"], acl_list=acl_list)

        # Creating a VPC network in the VPC
        self.debug("Creating a VPC network with Nuage VSP VPC Network "
                   "offering...")
        vpc_network = self.create_Network(
            network_offering, gateway='10.1.1.1', vpc=vpc, acl_list=acl_list)
        self.validate_Network(vpc_network, state="Implemented")
        with self.assertRaises(Exception):
            self.get_Router(vpc_network)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network)

        self.update_zone_details("testdomainname.com")
        vpc.restart(self.api_client)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network)

        # Deploying a VM in the VPC network
        self.vmdata["ipaddress"] = "10.1.1.2"
        vm = self.create_VM(vpc_network)
        self.check_VM_state(vm, state="Running")

        # VSD verification
        self.verify_vsd_network(self.domain.id, vpc_network, vpc)
        #self.verify_vsd_router(vr)
        self.verify_vsd_vm(vm)
        for nic in vm.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm", nic)
            self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        public_ip_1 = self.acquire_PublicIPAddress(vpc_network, vpc=vpc)
        self.create_StaticNatRule_For_VM(vm, public_ip_1, vpc_network)
        if not self.isSimulator:
            ssh_client = self.ssh_into_VM(vm, public_ip_1)
            self.verify_dhcp_on_vm("dhclient.eth0.leases", self.vmdata["details"]["dhcp:114"], ssh_client)
        vpc_network.restart(self.api_client, cleanup=True)

        vpc_network_1 = self.create_Network(
            network_offering, gateway='10.1.2.1', vpc=vpc, acl_list=acl_list)
        self.validate_Network(vpc_network, state="Implemented")
        with self.assertRaises(Exception):
            self.get_Router(vpc_network_1)

        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_1)
        vpc_network_1.restart(self.api_client, cleanup=True)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_1)
        #verify data with blank dhcp
        self.vmdata["ipaddress"] = "10.1.2.2"
        self.vmdata["details"] = {"dhcp:": "http://www.testdhcpfeature.com/adfsgbfgtdhh125ki-23-fdh-09"}
        self.vmdata["displayname"] = "vm1"
        self.vmdata["name"] = "vm1"
        # Deploying a VM in the VPC second tier with stop option
        vm_1 = self.create_VM(vpc_network_1)
        self.verify_vsd_vm(vm_1)
        for nic in vm_1.nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_value_notpresent("http://www.testdhcpfeature.com/adfsgbfgtdhh125ki-23-fdh-09", nic)
            self.verify_vsd_dhcp_option(12, "vm1", nic)
            self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        vm_1.stop(self.api_client)
        vm_1.start(self.api_client)


        vpc_network_2 = self.create_Network(
            network_offering, gateway='10.1.3.1', vpc=vpc, acl_list=acl_list)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_2)
        self.validate_Network(vpc_network, state="Implemented")
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_2)
        #verify data with big name and more key pair which need to be ignore
        self.vmdata["details"] = {"testdhcp11": "it is to test that other values is not pick by our plugins",
                                  "dhcp:114": "http://www.testdhcpfeature.com/adfsgbfgtdhh125ki-23-fdh-09:hgsggsggsgggsggs-agthstg@vmidtestitisagoodtest@innextonewewillhave",
                                  "dhcp_test": "httpjjjdjjdjjdjjdjjdjjd",
                                  "dhcp:1141": "one more test",
                                  "dhcp:98": "test98Notset"}
        self.vmdata["displayname"] = "vm2"
        self.vmdata["name"] = "vm2"
        self.vmdata["ipaddress"] = "10.1.3.2"
        # Deploying a VM in the VPC second tier with stop option
        vm_2 = self.create_VM(vpc_network_2, start_vm=False)
        vpc_network_2.restart(self.api_client, cleanup=True)
        vm_2.start(self.api_client)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_2)
        self.check_VM_state(vm_2, state="Running")
        for nic in vm_2.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm2", nic)
            self.verify_vsd_dhcp_option(15, "testvpc.com", nic)
            self.verify_vsd_dhcp_type_notpresent(98, nic)
            self.verify_vsd_dhcp_type_notpresent(1141, nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["testdhcp11"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp:1141"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp_test"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp:98"], nic)

        # Deleting the VM
        self.delete_VM(vm_2)
        vpc.restart(self.api_client)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_2)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_1)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network)

        #create a isolated network
        self.debug("Creating an Isolated network...")
        iso_net_off = self.create_NetworkOffering(
            self.test_data["nuagevsp"]["isolated_network_offering"])
        self.update_zone_details("testisolated.com")
        network = self.create_Network(iso_net_off)
        self.vmdata["displayname"] = "multinicvm"
        self.vmdata["name"] = "multinicvm"
        del self.vmdata["ipaddress"]

        #deploy a multiNic vm
        multinic_vm = self.create_VM([vpc_network, network])
        self.verify_vsd_vm(multinic_vm)
        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            self.verify_vsd_dhcp_type_notpresent(98, nic)
            self.verify_vsd_dhcp_type_notpresent(1141, nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["testdhcp11"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp:1141"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp_test"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp:98"], nic)
            if nic.networkid == network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        # commenting due to cloud-1164
        # self.verify_vsd_dhcp_option_subnet(15, "testisolated.com", network)
        update_response = Network.update(network, self.api_client, id=network.id, networkdomain="testisolated.com", changecidr=False)
        completeoutput = str(update_response).strip('[]')
        self.debug("network update response is " + completeoutput)
        self.assertEqual("testisolated.com", update_response.networkdomain, "Network Domain is not updated as expected")
        self.verify_vsd_dhcp_option_subnet(15, "testisolated.com", network)

        public_ip_1 = self.acquire_PublicIPAddress(vpc_network, vpc=vpc)
        self.create_StaticNatRule_For_VM(multinic_vm, public_ip_1, vpc_network)
        if not self.isSimulator:
            ssh_client = self.ssh_into_VM(multinic_vm, public_ip_1)
            self.verify_dhcp_on_vm("dhclient.eth0.leases", self.vmdata["details"]["dhcp:114"], ssh_client)
            #self.verify_dhcp_on_vm("dhclient.eth1.leases", self.vmdata["details"]["dhcp:114"], ssh_client)

        olddata = self.vmdata["details"]["dhcp:114"]
        self.vmdata["details"] = {"testdhcp11": "it is to test update without dhcp works fine"}

        self.update_vm_details(multinic_vm, self.vmdata["details"])

        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            self.verify_vsd_dhcp_value_notpresent(olddata, nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["testdhcp11"], nic)
            if nic.networkid == network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
                non_default_nic = nic
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        multinic_vm.update_default_nic(self.api_client, non_default_nic.id)
        self.vmdata["details"] = {"dhcp:114": "it is to test update with dhcp works fine"}
        self.update_vm_details(multinic_vm, self.vmdata["details"])

        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        # to verify for the blank value
        self.vmdata["details"] = {"dhcp:114": ""}
        self.update_vm_details(multinic_vm, self.vmdata["details"])
        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        self.vmdata["details"] = {"dhcp:114": "",
                                  "dhcp:114": "http://test.vmdetailfeature.com"}

        self.update_vm_details(multinic_vm, self.vmdata["details"])
        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_option(114, "http://test.vmdetailfeature.com", nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)
        multinic_vm.stop(self.api_client)
        multinic_vm.start(self.api_client)  #by restarting the vm the update default nic takes effect. -> default nic is know on isolated network and not vpc

        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_option(114, "http://test.vmdetailfeature.com", nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == vpc_network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testisolated.com", nic)

        self.vmdata["details"] = {"dhcp:13,114,15": "http://test.vmdetailfeature.com/testingit,test12,ththth"}
        self.update_vm_details(multinic_vm, self.vmdata["details"])
        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == vpc_network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testisolated.com", nic)

        #update nic before add NIC
        self.vmdata["details"] = {"dhcp:114": "http://test.vmdetailfeature.com/testingitforthelengthofmorethan74sowearewritingabigstringhopefullyitisalsoworkingfinewiththis"}
        self.update_vm_details(vm_1, self.vmdata["details"])
        for nic in vm_1.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm1", nic)
            self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        vm_1.add_nic(self.api_client, vpc_network.id)
        self.debug(
            "Going to verify if %s Network nic is present in virtual machine "
            "%s" % (vpc_network.name, vm_1.id))
        vm_list = list_virtual_machines(self.api_client, id=vm_1.id)
        vm_list_validation_result = validateList(vm_list)
        self.assertEqual(vm_list_validation_result[0], PASS,
                         "vm list validation failed due to %s" %
                         vm_list_validation_result[2])
        self.debug("virtual machine nics: %s" % vm_list[0].nic)
        # filter nic of virtual machine based on Network
        nics = [x for x in vm_list[0].nic if x.networkid == vpc_network.id]
        self.debug("Filtered nics list: %s:" % nics)
        self.assertEqual(len(nics), 1, "Expected nic found in vm with id %s " % vm_1.id)
        self.verify_vsd_vm(vm_list[0])
        for nic in vm_list[0].nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm1", nic)
            if nic.networkid == vpc_network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
                non_default_nic = nic
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        vm_1.update_default_nic(self.api_client, non_default_nic.id)
        # still router 3 option is not moved as it need stop/start
        for nic in vm_list[0].nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm1", nic)
            if nic.networkid == vpc_network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        vm_1.stop(self.api_client)
        vm_1.start(self.api_client)

        self.debug(
            "Going to verify if %s Network nic is present in virtual machine "
            "%s" % (vpc_network.name, vm_1.id))
        vm_list = list_virtual_machines(self.api_client, id=vm_1.id)
        vm_list_validation_result = validateList(vm_list)
        self.assertEqual(vm_list_validation_result[0], PASS,
                         "vm list validation failed due to %s" %
                         vm_list_validation_result[2])
        self.debug("virtual machine nics: %s" % vm_list[0].nic)
        # filter nic of virtual machine based on Network
        nics = [x for x in vm_list[0].nic if x.networkid == vpc_network.id]
        self.debug("Filtered nics list: %s:" % nics)
        self.assertEqual(len(nics), 1, "Expected nic found in vm with id %s " % vm_1.id)
        self.verify_vsd_vm(vm_list[0])

        for nic in vm_list[0].nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm1", nic)
            if nic.networkid == vpc_network_1.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
                non_default_nic = nic
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        vm_1.remove_nic(self.api_client, non_default_nic.id)

        vpc.restart(self.api_client)
        with self.assertRaises(Exception):
            self.get_Router(vpc_network)

        # Creating a VPC
        self.debug("Creating second VPC with Nuage VSP VPC offering...")
        self.update_zone_details("testvpc2.com")
        vpc2 = self.create_Vpc(vpc_offering, cidr='10.1.0.0/16')
        self.validate_Vpc(vpc2, state="Enabled")
        self.debug("Creating a VPC network with Nuage VSP VPC Network "
                   "offering...")
        vpc_network_vpc2 = self.create_Network(
            network_offering, gateway='10.1.1.1', vpc=vpc2)
        self.validate_Network(vpc_network_vpc2, state="Implemented")
        self.verify_vsd_dhcp_option_subnet(15, "testvpc2.com", vpc_network_vpc2)
        with self.assertRaises(Exception):
            self.get_Router(vpc_network_vpc2)

        vpc2.restart(self.api_client)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc2.com", vpc_network_vpc2)
        network.restart(self.api_client, cleanup=True)
        #self.verify_vsd_dhcp_option_subnet(15, "testvpc2.com", network)

        self.vmdata["details"] = {"dhcp:114": "http://test.vmdetailfeature.com/testingitforthelengthofmorethan74sowearewritingabigstringhopefullyitisalsoworkingfinewiththis"}

        self.update_vm_details(multinic_vm, self.vmdata["details"])
        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == vpc_network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testisolated.com", nic)

        vm.add_nic(self.api_client, vpc_network_vpc2.id)

        vm_list = list_virtual_machines(self.api_client, id=vm.id)
        vm_list_validation_result = validateList(vm_list)
        self.assertEqual(vm_list_validation_result[0], PASS,
                         "vm list validation failed due to %s" %
                         vm_list_validation_result[2])
        self.debug("virtual machine nics: %s" % vm_list[0].nic)
        # filter nic of virtual machine based on Network
        nics = [x for x in vm_list[0].nic if x.networkid == vpc_network_vpc2.id]
        self.debug("Filtered nics list: %s:" % nics)
        self.assertEqual(len(nics), 1, "Expected nic found in vm with id %s " % vm.id)
        self.verify_vsd_vm(vm_list[0])
        for nic in vm_list[0].nic:
            self.verify_vsd_dhcp_option(114, "http://www.testdhcpfeature.com/adfsgbfgtdhh125ki-23-fdh-09", nic)
            self.verify_vsd_dhcp_option(12, "vm", nic)
            if nic.networkid == vpc_network_vpc2.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        self.vmdata["details"] = {}
        self.vmdata["displayname"] = "vmvpc2"
        self.vmdata["name"] = "vmvpc2"
        self.vmdata["ipaddress"] = "10.1.1.2"
        vm_vpc2 = self.create_VM(vpc_network_vpc2)
        self.check_VM_state(vm_vpc2, state="Running")

        for nic in vm_vpc2.nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_option(12, "vmvpc2", nic)
            self.verify_vsd_dhcp_option(15, "testvpc2.com", nic)

        self.update_zone_details("testupdatenovpc.com")
        vpc2.restart(self.api_client)
        vpc_network_2_vpc2 = self.create_Network(
            network_offering, gateway='10.1.2.1', vpc=vpc2)
        vm_vpc2.add_nic(self.api_client, vpc_network_2_vpc2.id)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc2.com", vpc_network_2_vpc2)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc2.com", vpc_network_vpc2)

        vm_list = list_virtual_machines(self.api_client, id=vm_vpc2.id)
        vm_list_validation_result = validateList(vm_list)
        self.assertEqual(vm_list_validation_result[0], PASS,
                         "vm list validation failed due to %s" %
                         vm_list_validation_result[2])
        self.debug("virtual machine nics: %s" % vm_list[0].nic)
        # filter nic of virtual machine based on Network
        nics = [x for x in vm_list[0].nic if x.networkid == vpc_network_2_vpc2.id]
        self.debug("Filtered nics list: %s:" % nics)
        self.assertEqual(len(nics), 1, "Expected nic found in vm with id %s " % vm_vpc2.id)
        self.verify_vsd_vm(vm_list[0])
        for nic in vm_list[0].nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_option(12, "vmvpc2", nic)
            if nic.networkid == vpc_network_2_vpc2.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc2.com", nic)

        self.delete_VM(vm)
        self.delete_VM(vm_vpc2)
        self.delete_Network(vpc_network_2_vpc2)
        self.delete_Network(vpc_network_vpc2)
        vpc2.delete(self.api_client)
        if vpc2 in self.cleanup:
            self.cleanup.remove(vpc2)
        with self.assertRaises(Exception):
            self.verify_vsd_network(self.domain.id, vpc_network_vpc2, vpc2)

        vpc_network.restart(self.api_client, cleanup=True)
        del self.vmdata["ipaddress"]
        vpc_network_1.restart(self.api_client, cleanup=True)
        vpc_network_2.restart(self.api_client, cleanup=True)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_2)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_1)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network)
        with self.assertRaises(Exception):
            self.get_Router(vpc_network_2)


    @attr(tags=["advanced", "nuagevsp"], required_hardware="false")
    def test_02_nuage_domain_st_vpc_network(self):
        """ Test basic VPC Network functionality with Nuage domain template and dhcp support
        """

        # 1. Create Nuage VSP VPC offering, check if it is successfully
        #    created and enabled.
        # 2. Create a VPC with Nuage VSP VPC offering, check if it is
        #    successfully created and enabled.
        # 3. Create Nuage VSP VPC Network offering, check if it is successfully
        #    created and enabled.
        # 4. Create an ACL list in the created VPC, and add an ACL item to it.
        # 5. Create a VPC Network with Nuage VSP VPC Network offering and the
        #    created ACL list, check if it is successfully created, is in the
        #    "Implemented" state, and is added to the VPC VR.
        # 6. Deploy a VM in the created VPC network, check if the VM is
        #    successfully deployed and is in the "Running" state.
        # 7. Verify that the created ACL item is successfully implemented in
        #    Nuage VSP.
        # 8. Delete all the created objects (cleanup).

        domain_1 = Domain.create(
            self.api_client,
            self.domains_accounts_data["domain1"]
        )
        self.cleanup.append(domain_1)

        self.account_d1 = Account.create(
            self.api_client,
            self.domains_accounts_data["accountD1"],
            admin=True,
            domainid=domain_1.id
        )
        self.cleanup.append(self.account_d1)
        self.update_NuageVspGlobalDomainTemplateName("domain_template")

        # Creating a VPC offering
        self.debug("Creating Nuage VSP VPC offering without dhcp...")
        #self.test_data["nuagevsp"]["vpc_offering_nodhcp"]["supportedservices"] = 'Dhcp,StaticNat,SourceNat,Connectivity'
        #del self.test_data["nuagevsp"]["vpc_offering_nodhcp"]["serviceProviderList"]["NetworkACL"]
        vpc_offering = self.create_VpcOffering(
            self.test_data["nuagevsp"]["vpc_offering_nodhcp"])
        self.validate_VpcOffering(vpc_offering, state="Enabled")

        # Creating a VPC
        self.debug("Creating a VPC with Nuage VSP VPC offering...")
        vpc = self.create_Vpc(vpc_offering, cidr='10.1.0.0/16', account=self.account_d1, networkDomain="testvpc.com")

        # Creating a network offering
        self.debug("Creating Nuage VSP VPC Network offering without dhcp...")
        #self.test_data["nuagevsp"]["vpc_network_offering_nodhcp"]["supportedservices"] = 'Dhcp,StaticNat,SourceNat,Connectivity'
        #del self.test_data["nuagevsp"]["vpc_network_offering_nodhcp"]["serviceProviderList"]["NetworkACL"]
        network_offering = self.create_NetworkOffering(
            self.test_data["nuagevsp"]["vpc_network_offering_nodhcp"])
        self.validate_NetworkOffering(network_offering, state="Enabled")

        # Creating a VPC network in the VPC
        self.debug("Creating a VPC network with Nuage VSP VPC Network "
                   "offering...")
        vpc_network = self.create_Network(
            network_offering, gateway='10.1.1.1', vpc=vpc, account=self.account_d1)
        with self.assertRaises(Exception):
            self.get_Router(vpc_network)

        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network)

        self.update_zone_details("testdomainname.com")
        vpc.restart(self.api_client)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network)

        # Deploying a VM in the VPC network
        vm = self.create_VM(vpc_network, account=self.account_d1)
        self.check_VM_state(vm, state="Running")

        # VSD verification
        self.verify_vsd_network(self.domain.id, vpc_network, vpc, domain_template_name="domain_template")
        #self.verify_vsd_router(vr)
        self.verify_vsd_vm(vm)
        for nic in vm.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm", nic)
            self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        public_ip_1 = self.acquire_PublicIPAddress(vpc_network, vpc=vpc, account=self.account_d1)
        self.create_StaticNatRule_For_VM(vm, public_ip_1, vpc_network)
        vpc_network.restart(self.api_client, cleanup=True)

        vpc_network_1 = self.create_Network(
            network_offering, gateway='10.1.2.1', vpc=vpc, account=self.account_d1)
        with self.assertRaises(Exception):
            self.get_Router(vpc_network_1)

        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_1)
        vpc_network_1.restart(self.api_client, cleanup=True)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_1)
        #verify data with blank dhcp
        self.vmdata["details"] = {"dhcp:": "http://www.testdhcpfeature.com/adfsgbfgtdhh125ki-23-fdh-09"}
        self.vmdata["displayname"] = "vm1"
        self.vmdata["name"] = "vm1"
        # Deploying a VM in the VPC second tier with stop option
        vm_1 = self.create_VM(vpc_network_1, account=self.account_d1)
        self.verify_vsd_vm(vm_1)
        for nic in vm_1.nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_value_notpresent("http://www.testdhcpfeature.com/adfsgbfgtdhh125ki-23-fdh-09", nic)
            self.verify_vsd_dhcp_option(12, "vm1", nic)
            self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        vm_1.stop(self.api_client)
        vm_1.start(self.api_client)


        vpc_network_2 = self.create_Network(
            network_offering, gateway='10.1.3.1', vpc=vpc, account=self.account_d1)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_2)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_2)
        #verify data with big name and more key pair which need to be ignore
        self.vmdata["details"] = {"testdhcp11": "it is to test that other values is not pick by our plugins",
                                  "dhcp:114": "http://www.testdhcpfeature.com/adfsgbfgtdhh125ki-23-fdh-09:hgsggsggsgggsggs-agthstg@vmidtestitisagoodtest@innextonewewillhave",
                                  "dhcp_test": "httpjjjdjjdjjdjjdjjdjjd",
                                  "dhcp:1141": "one more test",
                                  "dhcp:98": "test98Notset"}
        self.vmdata["displayname"] = "vm2"
        self.vmdata["name"] = "vm2"
        # Deploying a VM in the VPC second tier with stop option
        vm_2 = self.create_VM(vpc_network_2, start_vm=False, account=self.account_d1)
        vpc_network_2.restart(self.api_client, cleanup=True)
        vm_2.start(self.api_client)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_2)
        self.check_VM_state(vm_2, state="Running")
        for nic in vm_2.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm2", nic)
            self.verify_vsd_dhcp_option(15, "testvpc.com", nic)
            self.verify_vsd_dhcp_type_notpresent(98, nic)
            self.verify_vsd_dhcp_type_notpresent(1141, nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["testdhcp11"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp:1141"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp_test"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp:98"], nic)

        # Deleting the VM
        self.delete_VM(vm_2)
        vpc.restart(self.api_client)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_2)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_1)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network)

        #create a isolated network
        self.debug("Creating an Isolated network...")
        iso_net_off = self.create_NetworkOffering(
            self.test_data["nuagevsp"]["isolated_network_offering"])
        self.update_zone_details("testisolated.com")
        network = self.create_Network(iso_net_off, account=self.account_d1)
        self.vmdata["displayname"] = "multinicvm"
        self.vmdata["name"] = "multinicvm"

        #deploy a multiNic vm
        multinic_vm = self.create_VM([vpc_network, network], account=self.account_d1)
        self.verify_vsd_vm(multinic_vm)
        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            self.verify_vsd_dhcp_type_notpresent(98, nic)
            self.verify_vsd_dhcp_type_notpresent(1141, nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["testdhcp11"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp:1141"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp_test"], nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["dhcp:98"], nic)
            if nic.networkid == network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        # commenting due to cloud-1164
        # self.verify_vsd_dhcp_option_subnet(15, "testisolated.com", network)
        update_response = Network.update(network, self.api_client, id=network.id, networkdomain="testisolated.com", changecidr=False)
        completeoutput = str(update_response).strip('[]')
        self.debug("network update response is " + completeoutput)
        self.assertEqual("testisolated.com", update_response.networkdomain, "Network Domain is not updated as expected")
        self.verify_vsd_dhcp_option_subnet(15, "testisolated.com", network)

        public_ip_1 = self.acquire_PublicIPAddress(vpc_network, vpc=vpc, account=self.account_d1)
        self.create_StaticNatRule_For_VM(multinic_vm, public_ip_1, vpc_network)

        olddata = self.vmdata["details"]["dhcp:114"]
        self.vmdata["details"] = {"testdhcp11": "it is to test update without dhcp works fine"}

        self.update_vm_details(multinic_vm, self.vmdata["details"])

        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            self.verify_vsd_dhcp_value_notpresent(olddata, nic)
            self.verify_vsd_dhcp_value_notpresent(self.vmdata["details"]["testdhcp11"], nic)
            if nic.networkid == network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
                non_default_nic = nic
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        multinic_vm.update_default_nic(self.api_client, non_default_nic.id)
        self.vmdata["details"] = {"dhcp:114": "it is to test update with dhcp works fine"}
        self.update_vm_details(multinic_vm, self.vmdata["details"])

        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        # to verify for the blank value
        self.vmdata["details"] = {"dhcp:114": ""}
        self.update_vm_details(multinic_vm, self.vmdata["details"])
        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        self.vmdata["details"] = {"dhcp:114": "",
                                  "dhcp:114": "http://test.vmdetailfeature.com"}

        self.update_vm_details(multinic_vm, self.vmdata["details"])
        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_option(114, "http://test.vmdetailfeature.com", nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)
        multinic_vm.stop(self.api_client)
        multinic_vm.start(self.api_client)  #by restarting the vm the update default nic takes effect. -> default nic is know on isolated network and not vpc

        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_option(114, "http://test.vmdetailfeature.com", nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == vpc_network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testisolated.com", nic)

        self.vmdata["details"] = {"dhcp:13,114,15": "http://test.vmdetailfeature.com/testingit,test12,ththth"}
        self.update_vm_details(multinic_vm, self.vmdata["details"])
        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == vpc_network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testisolated.com", nic)

        #update nic before add NIC
        self.vmdata["details"] = {"dhcp:114": "http://test.vmdetailfeature.com/testingitforthelengthofmorethan74sowearewritingabigstringhopefullyitisalsoworkingfinewiththis"}
        self.update_vm_details(vm_1, self.vmdata["details"])
        for nic in vm_1.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm1", nic)
            self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        vm_1.add_nic(self.api_client, vpc_network.id)
        self.debug(
            "Going to verify if %s Network nic is present in virtual machine "
            "%s" % (vpc_network.name, vm_1.id))
        vm_list = list_virtual_machines(self.api_client, id=vm_1.id)
        vm_list_validation_result = validateList(vm_list)
        self.assertEqual(vm_list_validation_result[0], PASS,
                         "vm list validation failed due to %s" %
                         vm_list_validation_result[2])
        self.debug("virtual machine nics: %s" % vm_list[0].nic)
        # filter nic of virtual machine based on Network
        nics = [x for x in vm_list[0].nic if x.networkid == vpc_network.id]
        self.debug("Filtered nics list: %s:" % nics)
        self.assertEqual(len(nics), 1, "Expected nic found in vm with id %s " % vm_1.id)
        self.verify_vsd_vm(vm_list[0])
        for nic in vm_list[0].nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm1", nic)
            if nic.networkid == vpc_network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
                non_default_nic = nic
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        vm_1.update_default_nic(self.api_client, non_default_nic.id)
        # still router 3 option is not moved as it need stop/start
        for nic in vm_list[0].nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm1", nic)
            if nic.networkid == vpc_network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        vm_1.stop(self.api_client)
        vm_1.start(self.api_client)

        self.debug(
            "Going to verify if %s Network nic is present in virtual machine "
            "%s" % (vpc_network.name, vm_1.id))
        vm_list = list_virtual_machines(self.api_client, id=vm_1.id)
        vm_list_validation_result = validateList(vm_list)
        self.assertEqual(vm_list_validation_result[0], PASS,
                         "vm list validation failed due to %s" %
                         vm_list_validation_result[2])
        self.debug("virtual machine nics: %s" % vm_list[0].nic)
        # filter nic of virtual machine based on Network
        nics = [x for x in vm_list[0].nic if x.networkid == vpc_network.id]
        self.debug("Filtered nics list: %s:" % nics)
        self.assertEqual(len(nics), 1, "Expected nic found in vm with id %s " % vm_1.id)
        self.verify_vsd_vm(vm_list[0])

        for nic in vm_list[0].nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "vm1", nic)
            if nic.networkid == vpc_network_1.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
                non_default_nic = nic
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        vm_1.remove_nic(self.api_client, non_default_nic.id)

        vpc.restart(self.api_client)
        with self.assertRaises(Exception):
            self.get_Router(vpc_network)

        # Creating a VPC
        self.debug("Creating second VPC with Nuage VSP VPC offering...")
        self.update_zone_details("testvpc2.com")
        vpc2 = self.create_Vpc(vpc_offering, cidr='10.1.0.0/16', account=self.account_d1)
        self.debug("Creating a VPC network with Nuage VSP VPC Network "
                   "offering...")
        vpc_network_vpc2 = self.create_Network(
            network_offering, gateway='10.1.1.1', vpc=vpc2, account=self.account_d1)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc2.com", vpc_network_vpc2)
        with self.assertRaises(Exception):
            self.get_Router(vpc_network_vpc2)

        vpc2.restart(self.api_client)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc2.com", vpc_network_vpc2)
        network.restart(self.api_client, cleanup=True)
        #self.verify_vsd_dhcp_option_subnet(15, "testvpc2.com", network)

        self.vmdata["details"] = {"dhcp:114": "http://test.vmdetailfeature.com/testingitforthelengthofmorethan74sowearewritingabigstringhopefullyitisalsoworkingfinewiththis"}

        self.update_vm_details(multinic_vm, self.vmdata["details"])
        for nic in multinic_vm.nic:
            self.verify_vsd_dhcp_option(114, self.vmdata["details"]["dhcp:114"], nic)
            self.verify_vsd_dhcp_option(12, "multinicvm", nic)
            if nic.networkid == vpc_network.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testisolated.com", nic)

        vm.add_nic(self.api_client, vpc_network_vpc2.id)

        vm_list = list_virtual_machines(self.api_client, id=vm.id)
        vm_list_validation_result = validateList(vm_list)
        self.assertEqual(vm_list_validation_result[0], PASS,
                         "vm list validation failed due to %s" %
                         vm_list_validation_result[2])
        self.debug("virtual machine nics: %s" % vm_list[0].nic)
        # filter nic of virtual machine based on Network
        nics = [x for x in vm_list[0].nic if x.networkid == vpc_network_vpc2.id]
        self.debug("Filtered nics list: %s:" % nics)
        self.assertEqual(len(nics), 1, "Expected nic found in vm with id %s " % vm.id)
        self.verify_vsd_vm(vm_list[0])
        for nic in vm_list[0].nic:
            self.verify_vsd_dhcp_option(114, "http://www.testdhcpfeature.com/adfsgbfgtdhh125ki-23-fdh-09", nic)
            self.verify_vsd_dhcp_option(12, "vm", nic)
            if nic.networkid == vpc_network_vpc2.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc.com", nic)

        self.vmdata["details"] = {}
        self.vmdata["displayname"] = "vmvpc2"
        self.vmdata["name"] = "vmvpc2"
        vm_vpc2 = self.create_VM(vpc_network_vpc2, account=self.account_d1)
        self.check_VM_state(vm_vpc2, state="Running")

        for nic in vm_vpc2.nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_option(12, "vmvpc2", nic)
            self.verify_vsd_dhcp_option(15, "testvpc2.com", nic)

        self.update_zone_details("testupdatenovpc.com")
        vpc2.restart(self.api_client)
        vpc_network_2_vpc2 = self.create_Network(
            network_offering, gateway='10.1.2.1', vpc=vpc2, account=self.account_d1)
        vm_vpc2.add_nic(self.api_client, vpc_network_2_vpc2.id)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc2.com", vpc_network_2_vpc2)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc2.com", vpc_network_vpc2)

        vm_list = list_virtual_machines(self.api_client, id=vm_vpc2.id)
        vm_list_validation_result = validateList(vm_list)
        self.assertEqual(vm_list_validation_result[0], PASS,
                         "vm list validation failed due to %s" %
                         vm_list_validation_result[2])
        self.debug("virtual machine nics: %s" % vm_list[0].nic)
        # filter nic of virtual machine based on Network
        nics = [x for x in vm_list[0].nic if x.networkid == vpc_network_2_vpc2.id]
        self.debug("Filtered nics list: %s:" % nics)
        self.assertEqual(len(nics), 1, "Expected nic found in vm with id %s " % vm_vpc2.id)
        self.verify_vsd_vm(vm_list[0])
        for nic in vm_list[0].nic:
            self.verify_vsd_dhcp_type_notpresent(114, nic)
            self.verify_vsd_dhcp_option(12, "vmvpc2", nic)
            if nic.networkid == vpc_network_2_vpc2.id:
                self.verify_vsd_dhcp_option(3, "0", nic)
                self.verify_vsd_dhcp_option_empty(15, nic)
            else:
                self.verify_vsd_dhcp_option(15, "testvpc2.com", nic)

        self.delete_VM(vm)
        self.delete_VM(vm_vpc2)
        self.delete_Network(vpc_network_2_vpc2)
        self.delete_Network(vpc_network_vpc2)
        vpc2.delete(self.api_client)
        if vpc2 in self.cleanup:
            self.cleanup.remove(vpc2)
        with self.assertRaises(Exception):
            self.verify_vsd_network(self.domain.id, vpc_network_vpc2, vpc2)

        vpc_network.restart(self.api_client, cleanup=True)
        vpc_network_1.restart(self.api_client, cleanup=True)
        vpc_network_2.restart(self.api_client, cleanup=True)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_2)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network_1)
        self.verify_vsd_dhcp_option_subnet(15, "testvpc.com", vpc_network)
        with self.assertRaises(Exception):
            self.get_Router(vpc_network_2)







