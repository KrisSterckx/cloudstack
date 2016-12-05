# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

"""
@Desc :
class UpdateDataCenter: Updates DeleteDataCenters according to a json
                        configuration file.
"""
from marvin import configGenerator
from marvin.cloudstackException import GetDetailExceptionInfo
from marvin.cloudstackAPI import listVPCOfferings, listVPCs, \
    listNetworkOfferings, listNetworks, updateNetwork, restartVPC, \
    createVPCOffering, createNetworkOffering, updateVPCOffering, \
    updateNetworkOffering, listDomains
from marvin.codes import (PASS, FAIL, FAILED, SUCCESS)
from marvin.jsonHelper import jsonLoader
from utils import boolean_input, numerical_input
from sys import exit
import os
import pprint
from optparse import OptionParser


class UpdateDataCenter(object):
    def __init__(self, test_client, cfg, logger=None, log_folder_path=None):
        self.__testClient = test_client
        self.__config = cfg
        self.__tcRunLogger = logger
        self.__logFolderPath = log_folder_path
        self.__apiClient = None
        self.__dbConnection = None
        self.__cleanUp = {}

    def setClient(self):
        self.__apiClient = self.__testClient.getApiClient()

    def setDbConnection(self):
        self.__dbConnection = self.__testClient.getDbConnection()

    def createVpcOffering(self, offering, askConfirmation=True):
        try:
            vpcoff = createVPCOffering.createVPCOfferingCmd()
            vpcoff.name = offering.name
            vpcoff.serviceproviderlist = []
            serviceproviders = convert_dict(offering.serviceOfferings)
            for s, p in serviceproviders.iteritems():
                sp = {'service': s, 'provider': p}
                vpcoff.serviceproviderlist.append(sp)
            vpcoff.supportedservices = serviceproviders.keys()
            vpcoff.displaytext = offering.name

            if askConfirmation:
                print "You are about to add a new VPC offering " \
                      "defined as:\n"
                pp = pprint.PrettyPrinter()
                pp.pprint(convert_dict(offering))

                if boolean_input("\nConfirm?"):
                    return self.__apiClient.createVPCOffering(vpcoff)
                else:
                    bye(PASS)

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("===Create VPC offering failed===")
            raise e

    def createNetworkOffering(self, offering, askConfirmation=True):
        try:
            netoff = createNetworkOffering.createNetworkOfferingCmd()
            netoff.name = offering.name
            netoff.traffictype = offering.trafficType
            netoff.guestiptype = offering.guestIpType
            netoff.ispersistent = offering.isPersistent
            netoff.conservemode = offering.conserveMode
            netoff.serviceproviderlist = []
            serviceproviders = convert_dict(offering.serviceOfferings)
            for s, p in serviceproviders.iteritems():
                sp = {'service': s, 'provider': p}
                netoff.serviceproviderlist.append(sp)
            netoff.supportedservices = serviceproviders.keys()
            netoff.displaytext = offering.name

            if askConfirmation:
                print "You are about to add a new network offering " \
                      "defined as:\n"
                pp = pprint.PrettyPrinter()
                pp.pprint(convert_dict(offering))

                if boolean_input("\nConfirm?"):
                    return self.__apiClient.createNetworkOffering(netoff)
                else:
                    bye(PASS)

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("=== Create Net offering failed ===")
            raise e

    def listVpcOfferings(self):
        try:
            listvpcoff = listVPCOfferings.listVPCOfferingsCmd()
            l = self.__apiClient.listVPCOfferings(listvpcoff)

            # reverse for usability
            if l:
                l.reverse()
            return l

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("=== List Vpc Offerings Failed ===")
            raise e

    def listDomain(self):
        """Lists domains"""
        try:
            cmd = listDomains.listDomainsCmd()
            cmd.listall = True
            l = self.__apiClient.listDomains(cmd)

            # reverse for usability
            if l:
                l.reverse()
            return l

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("=== List Domain Failed ===")
            raise e

    def listNetworkOfferings(self, forVpc):
        try:
            listnetoff = listNetworkOfferings.listNetworkOfferingsCmd()
            listnetoff.forvpc = forVpc
            l = self.__apiClient.listNetworkOfferings(listnetoff)

            # reverse for usability
            if l:
                l.reverse()
            return l

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("=== List Net Offerings Failed ===")
            raise e

    def findVpcOffering(self, offeringName):
        currentVpcOfferingsJson = self.listVpcOfferings()

        for off in currentVpcOfferingsJson:
            if off.name == offeringName:
                return off
        return None

    def findNetOffering(self, offeringName, forvpc):
        currentNetOfferingsJson = self.listNetworkOfferings(forvpc)

        for off in currentNetOfferingsJson:
            if off.name == offeringName:
                return off
        return None

    def listVpcs(self, domain=None):
        try:
            listvpc = listVPCs.listVPCsCmd()
            if domain:
                listvpc.domainid = domain.id
            l = self.__apiClient.listVPCs(listvpc)

            # reverse for usability
            if l:
                l.reverse()
            return l

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("=== List VPC's Failed ===")
            raise e

    def listNetworks(self, forVpc, vpcId=None, domain=None):
        try:
            listnet = listNetworks.listNetworksCmd()
            listnet.forvpc = forVpc
            if vpcId is not None:
                listnet.vpcid = vpcId
            if domain is not None:
                listnet.domainid = domain.id
            l = self.__apiClient.listNetworks(listnet)

            # reverse for usability
            if l:
                l.reverse()
            return l

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("=== List Networks Failed ===")
            raise e

    def enableVpcOffering(self, vpcOfferingId):
        try:
            enoff = updateVPCOffering.updateVPCOfferingCmd()
            enoff.id = vpcOfferingId
            enoff.state = "Enabled"
            self.__apiClient.updateVPCOffering(enoff)

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("=== Enable VPC off Failed ===")
            raise e

    def enableNetworkOffering(self, networkOfferingId):
        try:
            enoff = updateNetworkOffering.updateNetworkOfferingCmd()
            enoff.id = networkOfferingId
            enoff.state = "Enabled"
            self.__apiClient.updateNetworkOffering(enoff)

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("=== Enable Net off Failed ===")
            raise e

    def updateNetwork(self, networkId, networkOfferingId):
        try:
            upnet = updateNetwork.updateNetworkCmd()
            upnet.id = networkId
            upnet.networkofferingid = networkOfferingId
            self.__apiClient.updateNetwork(upnet)

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("=== Update Network Failed ===")
            raise e

    def restartVpc(self, vpcId):
        try:
            restart = restartVPC.restartVPCCmd()
            restart.id = vpcId
            self.__apiClient.restartVPC(restart)

        except Exception as e:
            print "Exception Occurred: %s" % GetDetailExceptionInfo(e)
            self.__tcRunLogger.exception("=== Restart VPC Failed ===")
            raise e

    def updateVpc(self, vpc, vpcOffering, netOffering, domain,
                  askConfirmation=True):

        print "\n==== Updating VPC %s ====" % vpc.name

        vpcTiers = self.listNetworks(True, vpc.id, domain)

        if askConfirmation:
            if vpcTiers:
                print "! %d VPC Tier networks found in VPC :" % len(vpcTiers),
                for tier in vpcTiers:
                    print tier.name,
                print
            else:
                print "No VPC Tier network found in VPC", vpc.name

            if not boolean_input(
                    "\nDo you confirm VPC " + vpc.name + " (" + vpc.id +
                    ") to be updated?\n"
                    "(each Tier network & the VPC will be restarted)"):
                print "Aborting update of VPC", vpc.name
                return

        '''
        Step1 : Upgrade VPC Tier Networks to the new offering
        '''
        print "\n=== 1. Updating VPC %s Tier networks ===" % vpc.name
        if vpcTiers:
            for tier in vpcTiers:
                print "! Updating network " + tier.name + \
                      " (" + tier.id + ")"
                self.updateNetwork(tier.id, netOffering.id)

            print "! All VPC %s Tier networks updated." % vpc.name
        else:
            print "No VPC Tier networks updated as VPC %s has none." % vpc.name

        '''
        Step2 : Upgrade the VPC to the new offering
        '''
        print "\n=== 2. Updating VPC %s itself ===" % vpc.name
        print "! Connecting to the CS SQL Server"
        self.setDbConnection()
        print "! Updating the VPC offering !"

        sql_cmd = "UPDATE vpc SET vpc_offering_id=(" + \
                  "SELECT id from vpc_offerings " + \
                  "where uuid=\"" + vpcOffering.id + \
                  "\") WHERE removed is null AND uuid = '" + vpc.id + "';"

        print "!", sql_cmd
        self.__dbConnection.execute(sql_cmd)
        print "! Updating VPC service map !"
        sql_cmd = "DELETE FROM vpc_service_map WHERE service" \
                  " = 'UserData' AND provider = 'NuageVspVpc' " \
                  "AND vpc_id = (SELECT id FROM vpc WHERE uuid = '" + \
                  vpc.id + "');"

        print "!", sql_cmd
        self.__dbConnection.execute(sql_cmd)

        print "! Restarting VPC", vpc.name
        self.restartVpc(vpc.id)
        print "! Done"

        print "\n==== Update of VPC %s is fully Done ====" % vpc.name

    def updateVpcPerVpc(self, vpcOffering, netOffering, execBulk=False):
        print "\n! Retrieving all Domains."
        domains = self.listDomain()
        domainCnt = len(domains)
        domainNbr = 0
        for domain in domains:
            print "! Retrieving all VPC's in Domain", domain.name
            vpcs = self.listVpcs(domain)
            if not vpcs:
                print "No VPC's found in Domain %s. No update needed." \
                      % domain.name
                domainNbr += 1
                if domainNbr < domainCnt:
                    print "Moving to next Domain."
                continue

            vpcDict = {}
            cnt = 0
            for vpc in vpcs:
                if vpc.vpcofferingid != vpcOffering.id:
                    cnt += 1
                    vpcDict[cnt] = vpc

            if cnt == 0:
                print "\nNo VPC's eligible for update found in Domain %s. " \
                      "No update needed." % domain.name
                domainNbr += 1
                if domainNbr < domainCnt:
                    print "Moving to next Domain."
                continue

            if execBulk:
                print "\n! Updating all VPC's in Domain", domain.name

                for nbr, vpc in vpcDict.items():
                    self.updateVpc(vpcDict[nbr], vpcOffering, netOffering,
                                   domain, False)

            else:
                while cnt > 0:
                    print "\nThe following VPC's are eligible for update " \
                          "within Domain", domain.name
                    print
                    for nbr, vpc in vpcDict.items():
                        print "[" + str(nbr) + "]", vpc.name

                    nbr = numerical_input(
                        "\nPlease select the VPC [1-" + str(cnt) +
                        "] or 0 if you want to skip this Domain", 0, cnt)

                    if nbr == 0:
                        break

                    self.updateVpc(vpcDict[nbr], vpcOffering, netOffering,
                                   domain)

                    vpcs.remove(vpcDict[nbr])
                    cnt = 0
                    for vpc in vpcs:
                        if vpc.vpcofferingid != vpcOffering.id:
                            cnt += 1
                            vpcDict[cnt] = vpc
                    del vpcDict[cnt+1]

            print "\nDone for Domain", domain.name
            domainNbr += 1
            if not execBulk and domainNbr < domainCnt and not boolean_input(
                    "Do you want to update VPC(s) in other Domains?",
                    default=False):
                return

        print "All Domains iterated over. Done."

    def updateDataCenter(self):
        try:
            print "\n=== Update Data Center Started ==="
            self.__tcRunLogger.debug(
                "\n====  Update Data Center Started ==== ")
            '''
            Step0 : Set the Client
            '''
            print "==== Connecting to the CS Mgnt Server ===="
            self.setClient()
            print "OK"

            '''
            Step1 : Create new VPC Offering
            '''
            print "\n==== Creating new VPC offering ===="
            vpcOfferingJson = self.__config.vpcOffering
            vpcOffering = None
            vpcOfferingPresentAlready = False

            if vpcOfferingJson:
                vpcOffering = self.findVpcOffering(vpcOfferingJson.name)
                if vpcOffering:
                    vpcOfferingPresentAlready = True
                else:
                    vpcOffering = self.createVpcOffering(vpcOfferingJson)
            else:
                print "No VPC offering specified in json config"
                exit(FAIL)

            print "\nThe specified vpc offering " + vpcOffering.name + \
                  (" exists already!" if vpcOfferingPresentAlready
                   else " is now present.")

            # enable the offering
            if vpcOffering.state != "Enabled":
                self.enableVpcOffering(vpcOffering.id)
                print "The specified vpc offering is now enabled."

            '''
            Step2 : Create new VPC Tier Offering
            '''
            print "\n==== Creating new VPC Tier offering ===="
            netOfferingJson = self.__config.vpcTierOffering
            netOffering = None
            netOfferingPresentAlready = False

            if netOfferingJson:
                netOffering = self.findNetOffering(netOfferingJson.name,
                                                   True)
                if netOffering:
                    netOfferingPresentAlready = True
                else:
                    netOffering = self.createNetworkOffering(netOfferingJson)
            else:
                print "No network offering specified in json config"
                exit(FAIL)

            print "\nThe specified network offering " + netOffering.name + \
                  (" exists already!" if netOfferingPresentAlready
                   else " is now present.")

            # enable the offering if needed
            if netOffering.state != "Enabled":
                self.enableNetworkOffering(netOffering.id)
                print "The specified network offering is now enabled."

            '''
            Step3 : Update existing deployment
            '''
            if not vpcOfferingPresentAlready or boolean_input(
                    "\nNo new VPC offering was added, "
                    "do you still want to update existing VPC's",
                    default=False):

                print "\n=== Updating the existing deployment ==="
                print "\n1. Update VPC per VPC"
                print "2. Update all existing VPC's in bulk"
                choice = numerical_input("Please make your choice",
                                         1, 2)
                if choice == 1:
                    self.updateVpcPerVpc(vpcOffering, netOffering)
                else:
                    self.updateVpcPerVpc(vpcOffering, netOffering, True)

            print "\n=== Update Data Center Successful ==="
            self.__tcRunLogger.debug(
                "\n=== Update Data Center Successful ===")
            return SUCCESS

        except Exception as e:
            print "\nException Occurred Under Update Data Center :%s" % \
                  GetDetailExceptionInfo(e)
            self.__tcRunLogger.debug(
                "\n=== Update Data Center Failed ===")
            print "\n=== Update Data Center Failed ==="
            return FAILED


def convert_dict(input):
    if isinstance(input, jsonLoader):
        return convert_dict(input.__dict__)
    if isinstance(input, dict):
        return dict((convert_dict(key), convert_dict(value))
                    for key, value in input.iteritems())
    elif isinstance(input, list):
        return [convert_dict(element) for element in input]
    elif isinstance(input, unicode):
        return input.encode('utf-8')
    else:
        return input


def bye(success=None):
    if success is not None:
        print "Bye " + ":)" if success else ":("
    else:
        print "Bye."
    exit(0 if success else -1)


if __name__ == "__main__":
    '''
    Step0a: Parse Command arguments
    '''
    parser = OptionParser()
    parser.add_option("-i", "--input", action="store",
                      default=None, dest="input",
                      help="the path to the json config file ")

    (options, args) = parser.parse_args()

    '''
    Step0b: Verify the Input validity
    '''
    if options.input is None:
        print "\n==== Please Specify a " \
              "Valid Input Configuration File===="
        exit(FAIL)

    '''
    Step0c: Imports the Modules Required
    '''
    from marvin.marvinLog import MarvinLog
    from marvin.cloudstackTestClient import CSTestClient

    '''
    Step1: Create the Logger
    '''
    if (options.input) and not (os.path.isfile(options.input)):
        print "\n=== Invalid Input Config File Path, Please Check ==="
        exit(FAIL)

    log_obj = MarvinLog("CSLog")
    cfg = configGenerator.getSetupConfig(options.input)
    log = cfg.logger

    ret = log_obj.createLogs("UpdateDataCenter", log)
    tc_run_logger = log_folder_path = None
    if ret != FAILED:
        log_folder_path = log_obj.getLogFolderPath()
        tc_run_logger = log_obj.getLogger()
    else:
        print "\n=== Log Creation Failed. Please Check ==="
        bye(FAIL)

    '''
    Step2 : Create Test Client
    '''
    obj_tc_client = CSTestClient(cfg.mgtSvr[0], cfg.dbSvr,
                                 logger=tc_run_logger)
    if obj_tc_client and obj_tc_client.createTestClient() == FAILED:
        print "\n=== TestClient Creation Failed ==="
        print "Management server not up?"
        bye(FAIL)

    '''
    Step3: Verify and Update Data Center
    '''
    if (options.input) and (os.path.isfile(options.input)):
        '''
        @Desc : Update Data Center
        '''
        update = UpdateDataCenter(obj_tc_client,
                                  cfg,
                                  tc_run_logger,
                                  log_folder_path=log_folder_path)
        if update.updateDataCenter() == FAILED:
            bye(FAIL)

    # All OK exit with 0 exitcode
    bye(PASS)
