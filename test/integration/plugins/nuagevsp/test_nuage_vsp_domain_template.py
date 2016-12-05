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

""" Component tests for Nuage VSP SDN plugin's Domain Template feature
"""
# Import Local Modules
from nuageTestCase import nuageTestCase
from marvin.lib.base import (Account,
                             Configurations,
                             Domain,
                             Network,
                             User,
                             VirtualMachine,
                             Zone)
# Import System Modules
from nose.plugins.attrib import attr


class TestNuageDomainTemplate(nuageTestCase):
    """Test Nuage VSP SDN plugin's Domain Template feature
    """

    @classmethod
    def setUpClass(cls):
        """
        Create the following domain tree and accounts that are required for
        executing Nuage VSP SDN plugin's Domain Template feature test cases:
            Under ROOT - Create a domain D1
            Under domain D1 - Create a subdomain D11
            Under each of the domains - create an admin user and a regular
            user account.
        Create Nuage VSP VPC and network (tier) offerings
        Create a VPC with a VPC network (tier) under each of the admin accounts
        of the above domains
        Create three pre-configured Nuage VSP domain templates per enterprise
        in VSD corresponding to each of the above domains
        """

        super(TestNuageDomainTemplate, cls).setUpClass()
        cls.domains_accounts_data = cls.test_data["acl"]

        try:
            # Backup default (ROOT admin user) apikey and secretkey
            cls.default_apikey = cls.api_client.connection.apiKey
            cls.default_secretkey = cls.api_client.connection.securityKey

            # Create domains
            cls.domain_1 = Domain.create(
                cls.api_client,
                cls.domains_accounts_data["domain1"]
            )
            cls._cleanup.append(cls.domain_1)

            cls.domain_11 = Domain.create(
                cls.api_client,
                cls.domains_accounts_data["domain11"],
                parentdomainid=cls.domain_1.id
            )
            cls._cleanup.append(cls.domain_11)

            # Create an admin and an user account under ROOT domain
            cls.account_root = Account.create(
                cls.api_client,
                cls.domains_accounts_data["accountROOT"],
                admin=True,
            )
            user = cls.generateKeysForUser(cls.api_client, cls.account_root)
            cls.user_root_apikey = user.apikey
            cls.user_root_secretkey = user.secretkey
            cls._cleanup.append(cls.account_root)

            cls.account_roota = Account.create(
                cls.api_client,
                cls.domains_accounts_data["accountROOTA"],
                admin=False,
            )
            user = cls.generateKeysForUser(cls.api_client, cls.account_roota)
            cls.user_roota_apikey = user.apikey
            cls.user_roota_secretkey = user.secretkey
            cls._cleanup.append(cls.account_roota)

            # Create an admin and an user account under domain D1
            cls.account_d1 = Account.create(
                cls.api_client,
                cls.domains_accounts_data["accountD1"],
                admin=True,
                domainid=cls.domain_1.id
            )
            user = cls.generateKeysForUser(cls.api_client, cls.account_d1)
            cls.user_d1_apikey = user.apikey
            cls.user_d1_secretkey = user.secretkey
            cls._cleanup.append(cls.account_d1)

            cls.account_d1a = Account.create(
                cls.api_client,
                cls.domains_accounts_data["accountD1A"],
                admin=False,
                domainid=cls.domain_1.id
            )
            user = cls.generateKeysForUser(cls.api_client, cls.account_d1a)
            cls.user_d1a_apikey = user.apikey
            cls.user_d1a_secretkey = user.secretkey
            cls._cleanup.append(cls.account_d1a)

            # Create an admin and an user account under subdomain D11
            cls.account_d11 = Account.create(
                cls.api_client,
                cls.domains_accounts_data["accountD11"],
                admin=True,
                domainid=cls.domain_11.id
            )
            user = cls.generateKeysForUser(cls.api_client, cls.account_d11)
            cls.user_d11_apikey = user.apikey
            cls.user_d11_secretkey = user.secretkey
            cls._cleanup.append(cls.account_d11)

            cls.account_d11a = Account.create(
                cls.api_client,
                cls.domains_accounts_data["accountD11A"],
                admin=False,
                domainid=cls.domain_11.id
            )
            user = cls.generateKeysForUser(cls.api_client, cls.account_d11a)
            cls.user_d11a_apikey = user.apikey
            cls.user_d11a_secretkey = user.secretkey
            cls._cleanup.append(cls.account_d11a)

            # Create VPC offering
            cls.vpc_offering = cls.create_VpcOffering(
                cls.test_data["nuagevsp"]["vpc_offering"])

            # Create VPC network (tier) offering
            cls.network_offering = cls.create_NetworkOffering(
                cls.test_data["nuagevsp"]["vpc_network_offering"])

            # Create a VPC with a VPC network (tier) under each of the admin
            # accounts of ROOT domain, domain D1, and subdomain D11
            # Create 500 pre-configured Nuage VSP domain templates per
            # enterprise in VSD corresponding to each of the above domains
            cls.cleanup_domain_templates = []
            cls.domain_template_list = []
            for i in range(0, 3):
                cls.domain_template_list.append("domain_template_" + str(i))

            # Check if Nuage VSP single tenancy mode is enabled
            cls.isNuageSingleTenancyModeEnabled = Configurations.list(
                cls.api_client,
                name="nuagevsp.multi.tenancy.enabled")[0].value == "false"

            if cls.isNuageSingleTenancyModeEnabled:
                accounts = [cls.account_root]
            else:
                accounts = [cls.account_root, cls.account_d1, cls.account_d11]
            for account in accounts:
                vpc = cls.create_Vpc(
                    cls.vpc_offering, cidr='10.1.0.0/16', account=account)
                cls.create_Network(
                    cls.network_offering,
                    vpc=vpc,
                    account=account)
                for domain_template in cls.domain_template_list:
                    new_domain_template = cls.vsdk.NUDomainTemplate(
                        name=domain_template,
                        description=domain_template)
                    enterprise = cls._session.user.enterprises.get_first(
                        filter="externalID BEGINSWITH '%s'" % account.domainid)
                    enterprise.create_child(new_domain_template)
                    cls.cleanup_domain_templates.append(
                        enterprise.domain_templates.get_first(
                            filter="name is '%s'" % domain_template))
        except Exception as e:
            cls.tearDownClass()
            raise Exception("Failed to create the setup required to execute "
                            "the test cases: %s" % e)
        return

    @classmethod
    def tearDownClass(cls):
        # Restore back default (ROOT admin user) apikey and secretkey
        cls.api_client.connection.apiKey = cls.default_apikey
        cls.api_client.connection.securityKey = cls.default_secretkey
        # Cleanup resources used
        cls.debug("Cleaning up the resources")
        for domain_template in cls.cleanup_domain_templates:
            try:
                domain_template.delete()
            except Exception as e:
                cls.error("Failed to cleanup domain template %s in VSD, got "
                          "%s" % (domain_template, e))
        cls.cleanup_domain_templates = []
        for obj in reversed(cls._cleanup):
            try:
                if isinstance(obj, VirtualMachine):
                    obj.delete(cls.api_client, expunge=True)
                else:
                    obj.delete(cls.api_client)
            except Exception as e:
                cls.error("Failed to cleanup %s, got %s" % (obj, e))
        try:
            cls.vpc_offering.delete(cls.api_client)
            cls.network_offering.delete(cls.api_client)
            cls.service_offering.delete(cls.api_client)
        except Exception as e:
            cls.error("Failed to cleanup offerings - %s" % e)
        # cleanup_resources(cls.api_client, cls._cleanup)
        cls._cleanup = []
        cls.debug("Cleanup complete!")
        return

    def setUp(self):
        self.account = self.account_root
        self.cleanup = []
        return

    def tearDown(self):
        # Restore back default (ROOT admin user) apikey and secretkey
        self.api_client.connection.apiKey = self.default_apikey
        self.api_client.connection.securityKey = self.default_secretkey
        # Cleanup resources used
        self.debug("Cleaning up the resources")
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

    @staticmethod
    def generateKeysForUser(api_client, account):
        user = User.list(
            api_client,
            account=account.name,
            domainid=account.domainid
        )[0]

        return (User.registerUserKeys(
            api_client,
            user.id
        ))

    # update_NuageVspGlobalDomainTemplateName - Updates the global setting
    # nuagevsp.vpc.domaintemplate.name with the given name
    def update_NuageVspGlobalDomainTemplateName(self, name):
        self.debug("Updating global setting nuagevsp.vpc.domaintemplate.name "
                   "with value - %s" % name)
        self.user_apikey = self.api_client.connection.apiKey
        self.user_secretkey = self.api_client.connection.securityKey
        self.api_client.connection.apiKey = self.default_apikey
        self.api_client.connection.securityKey = self.default_secretkey
        Configurations.update(self.api_client,
                              name="nuagevsp.vpc.domaintemplate.name",
                              value=name)
        self.api_client.connection.apiKey = self.user_apikey
        self.api_client.connection.securityKey = self.user_secretkey
        self.debug("Successfully updated global setting "
                   "nuagevsp.vpc.domaintemplate.name with value - %s" % name)

    # get_NuageVspGlobalDomainTemplateName - Returns the name of the
    # global/default pre-configured Nuage VSP domain template as mentioned in
    # the global setting "nuagevsp.vpc.domaintemplate.name"
    def get_NuageVspGlobalDomainTemplateName(self):
        self.user_apikey = self.api_client.connection.apiKey
        self.user_secretkey = self.api_client.connection.securityKey
        self.api_client.connection.apiKey = self.default_apikey
        self.api_client.connection.securityKey = self.default_secretkey
        domain_template_name = Configurations.list(
            self.api_client,
            name="nuagevsp.vpc.domaintemplate.name")[0].value
        self.api_client.connection.apiKey = self.user_apikey
        self.api_client.connection.securityKey = self.user_secretkey
        return domain_template_name

    @attr(tags=["advanced", "nuagevsp"], required_hardware="false")
    def test_01_nuage_Global_Domain_Template(self):
        """Test Nuage VSP Global Domain Template
        """

        for zone in self.zones:
            self.debug("Zone - %s" % zone.name)
            self.user_apikey = self.api_client.connection.apiKey
            self.user_secretkey = self.api_client.connection.securityKey
            self.api_client.connection.apiKey = self.default_apikey
            self.api_client.connection.securityKey = self.default_secretkey
            # Get Zone details
            self.getZoneDetails(zone=zone)
            # Configure VSD sessions
            self.configureVSDSessions()
            self.api_client.connection.apiKey = self.user_apikey
            self.api_client.connection.securityKey = self.user_secretkey

            # Updating global setting "nuagevsp.vpc.domaintemplate.name"
            self.update_NuageVspGlobalDomainTemplateName(
                name="invalid_domain_template")
            domain_template_name = self.get_NuageVspGlobalDomainTemplateName()
            self.assertEqual(domain_template_name, "invalid_domain_template",
                             "Global setting nuagevsp.vpc.domaintemplate.name was "
                             "not updated successfully"
                             )

            # Creating VPC and VPC networks (tiers)
            vpc = self.create_Vpc(self.vpc_offering, cidr='10.1.0.0/16')
            with self.assertRaises(Exception):
                self.create_Network(
                    self.network_offering, gateway='10.1.1.1', vpc=vpc)
            self.debug("VPC network creation fails as there is no domain "
                       "template with name invalid_domain_template in VSD")
            with self.assertRaises(Exception):
                self.create_Network(
                    self.network_offering, gateway='10.1.2.1', vpc=vpc)
            self.debug("VPC network creation fails as there is no domain "
                       "template with name invalid_domain_template in VSD")

            # Updating global setting "nuagevsp.vpc.domaintemplate.name"
            self.update_NuageVspGlobalDomainTemplateName(
                name=self.domain_template_list[0].upper())
            domain_template_name = self.get_NuageVspGlobalDomainTemplateName()
            self.assertEqual(domain_template_name,
                             self.domain_template_list[0].upper(),
                             "Global setting nuagevsp.vpc.domaintemplate.name was "
                             "not updated successfully"
                             )

            # Creating VPC and VPC networks (tiers)
            vpc_0 = self.create_Vpc(self.vpc_offering, cidr='10.1.0.0/16')
            with self.assertRaises(Exception):
                self.create_Network(
                    self.network_offering, gateway='10.1.1.1', vpc=vpc_0)
            self.debug("VPC network creation fails as there is a case "
                       "mismatch in the configured Nuage VSP global domain "
                       "template name")
            with self.assertRaises(Exception):
                self.create_Network(
                    self.network_offering, gateway='10.1.2.1', vpc=vpc_0)
            self.debug("VPC network creation fails as there is a case "
                       "mismatch in the configured Nuage VSP global domain "
                       "template name")

            # Updating global setting "nuagevsp.vpc.domaintemplate.name"
            self.update_NuageVspGlobalDomainTemplateName(
                name=self.domain_template_list[0])
            domain_template_name = self.get_NuageVspGlobalDomainTemplateName()
            self.assertEqual(domain_template_name, self.domain_template_list[0],
                             "Global setting nuagevsp.vpc.domaintemplate.name was "
                             "not updated successfully"
                             )

            # Creating VPC and VPC networks (tiers)
            vpc_1 = self.create_Vpc(self.vpc_offering, cidr='10.1.0.0/16')
            vpc_1_tier_1 = self.create_Network(
                self.network_offering, gateway='10.1.1.1', vpc=vpc_1)
            vpc_1_tier_2 = self.create_Network(
                self.network_offering, gateway='10.1.2.1', vpc=vpc_1)

            # VSD verification
            self.verify_vsd_network(
                self.account.domainid, vpc_1_tier_1, vpc_1,
                domain_template_name=domain_template_name)
            self.verify_vsd_network(
                self.account.domainid, vpc_1_tier_2, vpc_1,
                domain_template_name=domain_template_name)

            # Restart VPC networks (tiers) without cleanup
            Network.restart(vpc_1_tier_1, self.api_client, cleanup=False)
            Network.restart(vpc_1_tier_2, self.api_client, cleanup=False)

            # VSD verification
            self.verify_vsd_network(
                self.account.domainid, vpc_1_tier_1, vpc_1,
                domain_template_name=domain_template_name)
            self.verify_vsd_network(
                self.account.domainid, vpc_1_tier_2, vpc_1,
                domain_template_name=domain_template_name)

            # Restart VPC networks (tiers) with cleanup
            Network.restart(vpc_1_tier_1, self.api_client, cleanup=True)
            Network.restart(vpc_1_tier_2, self.api_client, cleanup=True)

            # VSD verification
            self.verify_vsd_network(
                self.account.domainid, vpc_1_tier_1, vpc_1,
                domain_template_name=domain_template_name)
            self.verify_vsd_network(
                self.account.domainid, vpc_1_tier_2, vpc_1,
                domain_template_name=domain_template_name)

            # Restart VPC
            vpc_1.restart(self.api_client)

            # VSD verification
            self.verify_vsd_network(
                self.account.domainid, vpc_1_tier_1, vpc_1,
                domain_template_name=domain_template_name)
            self.verify_vsd_network(
                self.account.domainid, vpc_1_tier_2, vpc_1,
                domain_template_name=domain_template_name)

            # Updating global setting "nuagevsp.vpc.domaintemplate.name"
            self.update_NuageVspGlobalDomainTemplateName(
                name=self.domain_template_list[0])
            domain_template_name = self.get_NuageVspGlobalDomainTemplateName()
            self.assertEqual(domain_template_name, self.domain_template_list[0],
                             "Global setting nuagevsp.vpc.domaintemplate.name was "
                             "not updated successfully"
                             )

            # Creating VPC and VPC networks (tiers)
            vpc_2 = self.create_Vpc(self.vpc_offering, cidr='10.1.0.0/16')
            vpc_2_tier_1 = self.create_Network(
                self.network_offering, gateway='10.1.1.1', vpc=vpc_2)
            vpc_2_tier_2 = self.create_Network(
                self.network_offering, gateway='10.1.2.1', vpc=vpc_2)

            # VSD verification
            self.verify_vsd_network(
                self.account.domainid, vpc_2_tier_1, vpc_2,
                domain_template_name=domain_template_name)
            self.verify_vsd_network(
                self.account.domainid, vpc_2_tier_2, vpc_2,
                domain_template_name=domain_template_name)

            # Updating global setting "nuagevsp.vpc.domaintemplate.name"
            self.update_NuageVspGlobalDomainTemplateName(
                name=self.domain_template_list[1])
            domain_template_name = self.get_NuageVspGlobalDomainTemplateName()
            self.assertEqual(domain_template_name, self.domain_template_list[1],
                             "Global setting nuagevsp.vpc.domaintemplate.name was "
                             "not updated successfully"
                             )

            # Creating VPC and VPC networks (tiers)
            vpc_3 = self.create_Vpc(self.vpc_offering, cidr='10.1.0.0/16')
            vpc_3_tier_1 = self.create_Network(
                self.network_offering, gateway='10.1.1.1', vpc=vpc_3)
            vpc_3_tier_2 = self.create_Network(
                self.network_offering, gateway='10.1.2.1', vpc=vpc_3)

            # VSD verification
            self.verify_vsd_network(
                self.account.domainid, vpc_3_tier_1, vpc_3,
                domain_template_name=domain_template_name)
            self.verify_vsd_network(
                self.account.domainid, vpc_3_tier_2, vpc_3,
                domain_template_name=domain_template_name)

            # Updating global setting "nuagevsp.vpc.domaintemplate.name"
            self.update_NuageVspGlobalDomainTemplateName(name="")

    @attr(tags=["advanced", "nuagevsp"], required_hardware="false")
    def test_02_nuage_Global_Domain_Template_as_ROOT_user(self):
        """Test Nuage VSP Global Domain Template as ROOT domain regular user
        """

        # Repeat the tests in the testcase
        # "test_01_nuage_Global_Domain_Template" as ROOT domain regular user

        # Setting ROOT domain user account information
        self.account = self.account_roota

        # Setting ROOT domain user keys in api_client
        self.api_client.connection.apiKey = self.user_roota_apikey
        self.api_client.connection.securityKey = self.user_roota_secretkey

        # Calling testcase "test_07_nuage_Global_Domain_Template"
        self.test_01_nuage_Global_Domain_Template()

    @attr(tags=["advanced", "nuagevsp"], required_hardware="false")
    def test_03_nuage_Global_Domain_Template_as_domain_admin(self):
        """Test Nuage VSP Global Domain Template as domain admin user
        """

        # Repeat the tests in the testcase
        # "test_01_nuage_Global_Domain_Template" as domain admin user

        # Setting domain D1 admin account information
        self.account = self.account_d1

        # Setting domain D1 admin keys in api_client
        self.api_client.connection.apiKey = self.user_d1_apikey
        self.api_client.connection.securityKey = self.user_d1_secretkey

        # Calling testcase "test_07_nuage_Global_Domain_Template"
        self.test_01_nuage_Global_Domain_Template()

    @attr(tags=["advanced", "nuagevsp"], required_hardware="false")
    def test_04_nuage_Global_Domain_Template_as_domain_user(self):
        """Test Nuage VSP Global Domain Template as domain regular user
        """

        # Repeat the tests in the testcase
        # "test_01_nuage_Global_Domain_Template" as domain regular user

        # Setting domain D1 user account information
        self.account = self.account_d1a

        # Setting domain D1 user keys in api_client
        self.api_client.connection.apiKey = self.user_d1a_apikey
        self.api_client.connection.securityKey = self.user_d1a_secretkey

        # Calling testcase "test_07_nuage_Global_Domain_Template"
        self.test_01_nuage_Global_Domain_Template()

    @attr(tags=["advanced", "nuagevsp"], required_hardware="false")
    def test_05_nuage_Global_Domain_Template_as_subdomain_admin(self):
        """Test Nuage VSP Global Domain Template as subdomain admin user
        """

        # Repeat the tests in the testcase
        # "test_01_nuage_Global_Domain_Template" as subdomain admin user

        # Setting subdomain D11 admin account information
        self.account = self.account_d11

        # Setting subdomain D1 admin keys in api_client
        self.api_client.connection.apiKey = self.user_d11_apikey
        self.api_client.connection.securityKey = self.user_d11_secretkey

        # Calling testcase "test_07_nuage_Global_Domain_Template"
        self.test_01_nuage_Global_Domain_Template()

    @attr(tags=["advanced", "nuagevsp"], required_hardware="false")
    def test_06_nuage_Global_Domain_Template_as_subdomain_user(self):
        """Test Nuage VSP Global Domain Template as subdomain regular user
        """

        # Repeat the tests in the testcase
        # "test_01_nuage_Global_Domain_Template" as subdomain regular user

        # Setting subdomain D11 user account information
        self.account = self.account_d11a

        # Setting subdomain D11 user keys in api_client
        self.api_client.connection.apiKey = self.user_d11a_apikey
        self.api_client.connection.securityKey = self.user_d11a_secretkey

        # Calling testcase "test_07_nuage_Global_Domain_Template"
        self.test_01_nuage_Global_Domain_Template()
