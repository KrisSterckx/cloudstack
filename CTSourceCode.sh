#!/bin/bash
time_stamp=$(date +%Y_%m_%d_%H%M%S)
gzfilename=acs45changes_"${time_stamp}"
cloudstackversion=4.5.1
cloudstackbuildname=4.5.1-SNAPSHOT
mkdir -p "${gzfilename}"
git diff --name-only 272dab1596f8565a72ac0f6fbe073785fa03a11d | grep -v adapter | grep -v vhd-util | grep -v deps > "${gzfilename}"/tar_list;
tar -cf - -T "${gzfilename}"/tar_list | (cd "${gzfilename}" && tar xf -)
rm -rf "${gzfilename}"/tar_list
sed -i".bak" '25d' "${gzfilename}"/plugins/network-elements/nuage-vsp/pom.xml
rm -rf "${gzfilename}"/plugins/network-elements/nuage-vsp/pom.xml.bak
rm -rf "${gzfilename}"/CTSourceCode.sh
cd "${gzfilename}"
tar -zcf "${gzfilename}".tar.gz *
cd ..
cp "${gzfilename}"/"${gzfilename}".tar.gz dist/rpmbuild/RPMS
cp dist/rpmbuild/BUILD/cloudstack-${cloudstackbuildname}/plugins/network-elements/nuage-vsp/src/adapter/target/cloud-plugin-network-vsp-client-${cloudstackbuildname}.jar dist/rpmbuild/RPMS/cloud-plugin-network-vsp-client-${cloudstackversion}_"${time_stamp}".jar
rm -rf "${gzfilename}"
