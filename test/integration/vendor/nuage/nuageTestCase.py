from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.cloudstackAPI import updateConfiguration, listConfigurations
from marvin.lib.base import (ServiceOffering,
                                         NetworkOffering,
                                         Network,
                                         Nuage,
                                         VPC,
                                         VpcOffering,
                                         PhysicalNetwork,
                                         PublicIPAddress,
                                         VirtualMachine,
                                         StaticNATRule)
from marvin.lib.common import (get_zone,
                                           get_domain,
                                           get_template)
from marvin.lib.utils import cleanup_resources
from nose.plugins.attrib import attr
import unittest
import types


# import vspk.vsdk.v3_2 as vsdk
# from vspk.vsdk.v3_2.utils import set_log_level

import logging
import socket

def nuage_disabled():
    def decorator(test_item):
        if not isinstance(test_item, (type, types.ClassType)):
            import functools
            @functools.wraps(test_item)
            def skip_wrapper(*args, **kwargs):
                cls = args[0]
                pnids = [pn.id for pn in PhysicalNetwork.list(cls.apiClient, zoneid=cls.zone.id) if pn.isolationmethods=='VSP']
                if len(Nuage.list(cls.apiClient, physicalnetworkid=pnids)) == 0:
                    cls.debug("VSD not found :(, skipping the test")
                    raise unittest.SkipTest("VSP is not configured")
                else:
                    cls.debug("VSD found, execute the test :)")
                    #test_item(*args, **kwargs)
                    #test_item = skip_wrapper

        #test_item.__unittest_skip__ = True
        #test_item.__unittest_skip_why__ = "VSP is not configured"
        return test_item
    return decorator



class nuageTestCase(cloudstackTestCase):
    @classmethod
    def setUpClass(cls):
        cls.debug("setUpClass nuageTestCase")

        # We want to fail quicker if it's failure
        socket.setdefaulttimeout(60)

        cls.logger = logging.getLogger('nuageTestCase')

        try:
            import vspk.vsdk.v3_2 as vsdk
            from vspk.vsdk.v3_2.utils import set_log_level
        except:
            raise unittest.SkipTest("Vspk is not Installed on the system")

        set_log_level(logging.INFO)

        testClient = super(nuageTestCase, cls).getClsTestClient()
        cls.apiclient = testClient.getApiClient()
        cls.testdata = testClient.getParsedTestDataConfig()

        cls.domain = get_domain(cls.apiclient)
        cls.zone = get_zone(cls.apiclient)
        try:
            cls.template = get_template(
                cls.apiclient,
                cls.zone.id,
                cls.testdata["ostype"]
            )
        except:
            cls.template = get_template(
                cls.apiclient,
                cls.zone.id,
                cls.testdata["ostype"].replace("64-bit", "32-bit").replace("5.5", "5.3")
            )

        cls.testdata["virtual_machine"]["zoneid"] = cls.zone.id
        cls.testdata["virtual_machine"]["template"] = cls.template.id

        cls.service_offering = ServiceOffering.create(
            cls.apiclient,
            cls.testdata["service_offering"]
        )
        cls._cleanup = [cls.service_offering]

        pnids = [pn.id for pn in PhysicalNetwork.list(cls.apiclient, zoneid=cls.zone.id) if pn.isolationmethods=='VSP']
        if len(pnids) == 0:
            cls.debug("VSP Physical Network not found :(, skipping the test")
            raise unittest.SkipTest("VSP is not configured")

        cls.vsp_physicalnetworkid = [x.id for x in PhysicalNetwork.list(cls.apiclient, zoneid=cls.zone.id) if x.isolationmethods=='VSP'][0]

        vsp_devices =  Nuage.list(cls.apiclient, physicalnetworkid=cls.vsp_physicalnetworkid)

        if len(vsp_devices) == 0:
            cls.debug("VSD not found :(, skipping the test")
            raise unittest.SkipTest("VSP is not configured")

        cls.vsp_device = Nuage(vsp_devices[0].__dict__)

        pynetworks = cls.config.zones[0].physical_networks
        providers = filter(lambda physical_network: 'VSP' in physical_network.isolationmethods, pynetworks)[0].providers
        devices = filter(lambda provider: provider.name == 'NuageVsp', providers)[0].devices

        cls.vspk_session = vsdk.NUVSDSession(username=devices[0].username, password=devices[0].password, enterprise="csp", api_url="https://%s:%d" % (cls.vsp_device.hostname, cls.vsp_device.port))
        cls.vspk_session.start()

        cls.debug("setUpClass nuageTestCase [DONE]")

    @classmethod
    def setUp(self):
        self.cleanup = []
        return

    @classmethod
    def tearDownClass(cls):
        try:
            #Cleanup resources used
            cleanup_resources(cls.apiclient, cls._cleanup)
        except Exception as e:
            cls.debug ("Warning: Exception during cleanup : %s" % e)
            #raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    def tearDown(self):
        try:
            #Clean up, terminate the created network offerings
            cleanup_resources(self.apiclient, self.cleanup)
        except Exception as e:
            self.debug("Warning: Exception during cleanup : %s" % e)
            #raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    def create_Vpc(self, offering_name="Nuage VSP VPC offering", cidr='10.1.1.1/16', cleanup=True):
        vpc_offerings = VpcOffering.list(self.apiclient, name=offering_name)
        self.assert_(vpc_offerings is not None and len(vpc_offerings)>0, "Nuage VPC offering not found")
        vpc_offering = vpc_offerings[0]
        self.debug("Creating a VPC network in the account: %s" % self.account.name)
        self.testdata["vpc"]["cidr"] = cidr
        vpc = VPC.create(
            self.apiclient,
            self.testdata["vpc"],
            vpcofferingid=vpc_offering.id,
            zoneid=self.zone.id,
            account=self.account.name,
            domainid=self.account.domainid
        )
        if cleanup:
            self.cleanup.append(vpc)
        return vpc

    def create_NetworkOffering(self, net_offerring, suffix=None):
        try:
            self.debug('Create NetworkOffering')
            if suffix:
                net_offerring["name"] = "NET_OFF-" + str(suffix)
            nw_off = NetworkOffering.create(self.apiclient,
                                            net_offerring,
                                            conservemode=False
            )

            # Enable Network offering
            nw_off.update(self.apiclient, state='Enabled')
            self.cleanup.append(nw_off)
            self.debug('Created and Enabled NetworkOffering')

            return nw_off
        except Exception as e:
            self.debug("Unable to create a Network offering: %s" % e)
            self.fail('Unable to create a Network offering=%s' % net_offerring)

    def fetch_by_external_id(self, fetcher, *cs_objects):
        """ Fetches a child object by external id using the given fetcher, and uuids of the given cloudstack objects.
        E.G.
          - fetch_by_external_id(vsdk.NUSubnet(id="954de425-b860-410b-be09-c560e7dbb474").vms, cs_vm)
          - fetch_by_external_id(session.user.floating_ips, cs_network, cs_public_ip)
        :param fetcher: VSPK Fetcher to use to find the child entity
        :param cs_objects: Cloudstack objects to take the UUID from.
        :return: the VSPK object having the correct externalID
        """
        return fetcher.get_first(filter="externalID BEGINSWITH '%s'" % ":".join([o.id for o in cs_objects]))

    def create_Network(self, nw_off, gateway='10.1.1.1',vpc=None):
        if not hasattr(nw_off, "id"):
            nw_off = self.create_NetworkOffering(nw_off, gateway)

        try:
            self.debug('Adding Network=%s' % self.testdata["network"])
            obj_network = Network.create(self.apiclient,
                                         self.testdata["network"],
                                         accountid=self.account.name,
                                         domainid=self.account.domainid,
                                         networkofferingid=nw_off.id,
                                         zoneid=self.zone.id,
                                         gateway=gateway,
                                         vpcid=vpc.id if vpc else self.vpc.id if hasattr(self, "vpc") else None
            )
            self.debug("Created network with ID: %s" % obj_network.id)
            self.cleanup.append(obj_network)
            return obj_network
        except Exception as e:
            self.debug("Unable to create a Network with offering: %s" % e)
            self.fail('Unable to create a Network with offering=%s' % nw_off)

    def upgrade_Network(self, nw_off, network):
        if not hasattr(nw_off, "id"):
            nw_off = self.create_NetworkOffering(nw_off, network.gateway)

        try:
            self.debug('Update Network=%s' % network)
            network.update(
                self.apiclient,
                networkofferingid=nw_off.id,
                changecidr=False
            )
            self.debug("Updated network with ID: %s" % network.id)
            return
        except Exception as e:
            self.debug("Unable to upgrade Network to offering: %s" % e)
            self.fail('Unable to upgrade Network to offering=%s' % nw_off)

    def create_VM_in_Network(self, network, host_id=None):
        try:
            self.debug('Creating VM in network=%s' % network.name)
            vm = VirtualMachine.create(
                self.apiclient,
                self.testdata["virtual_machine"],
                accountid=self.account.name,
                domainid=self.account.domainid,
                serviceofferingid=self.service_offering.id,
                networkids=[str(network.id)],
                hostid=host_id
            )
            self.debug('Created VM=%s in network=%s' % (vm.id, network.name))
            self.cleanup.append(vm)
            return vm
        except:
            self.fail('Unable to create VM in a Network=%s' % network.name)

    def acquire_Public_IP(self, network, vpc=None):
        self.debug("Associating public IP for network: %s" % network.name)
        public_ip = PublicIPAddress.create(self.apiclient,
                                           accountid=self.account.name,
                                           zoneid=self.zone.id,
                                           domainid=self.account.domainid,
                                           networkid=None, #network.id,
                                           vpcid=vpc.id if vpc else self.vpc.id if hasattr(self, "vpc") else None
        )
        self.debug("Associated %s with network %s" % (public_ip.ipaddress.ipaddress,
                                                      network.id
        ))
        return public_ip

    def create_StaticNatRule_For_VM(self, vm, public_ip, network, vmguestip=None):
        self.debug("Enabling static NAT for IP: %s" %
                       public_ip.ipaddress.ipaddress)
        try:
            StaticNATRule.enable(
                self.apiclient,
                ipaddressid=public_ip.ipaddress.id,
                virtualmachineid=vm.id,
                networkid=network.id,
                vmguestip=vmguestip
            )
            self.debug("Static NAT enabled for IP: %s" %
                       public_ip.ipaddress.ipaddress)
        except Exception as e:
            self.fail("Failed to enable static NAT on IP: %s - %s" % (
                public_ip.ipaddress.ipaddress, e))

    def delete_StaticNatRule_For_VM(self, vm, public_ip):
        self.debug("Disabling static NAT for IP: %s" %
                   public_ip.ipaddress.ipaddress)
        try:
            StaticNATRule.disable(
                self.apiclient,
                ipaddressid=public_ip.ipaddress.id,
                virtualmachineid=vm.id,
                )
            self.debug("Static NAT disabled for IP: %s" %
                       public_ip.ipaddress.ipaddress)
        except Exception as e:
            self.fail("Failed to disabled static NAT on IP: %s - %s" % (
                public_ip.ipaddress.ipaddress, e))

    def enable_source_nat_config(self, enable):
        cmd = updateConfiguration.updateConfigurationCmd()
        cmd.name = "nuagevsp.sourcenat.enabled"
        cmd.zoneid = self.zone.id
        cmd.value = str(enable).lower()
        self.apiclient.updateConfiguration(cmd)

    def getConfigurationValue(self, name):
        listConfigurationsCmd = listConfigurations.listConfigurationsCmd()
        listConfigurationsCmd.name = name
        listConfigurationsCmd.scopename = "global"
        return self.apiclient.listConfigurations(listConfigurationsCmd)

