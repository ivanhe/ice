#!/usr/bin/tcsh
#
#  input: JET distribution tar
#         clone-dir
#  output:  ICE distribution tar
#
# Run this script in a fresh directory
#
setenv JET_PACKAGE $JET_HOME/jet-170101.tar.gz
setenv JAVA_TOOL_OPTIONS "-Dfile.encoding=UTF-8"
rm -rf ice-bin
mkdir ice-bin
cd ice-bin
curl http://daringfireball.net/projects/downloads/Markdown_1.0.1.zip > Markdown_1.0.1.zip
unzip Markdown_1.0.1.zip
echo "Unpacking Jet package..."
tar zxvf $JET_PACKAGE
mv props jet-props
git clone https://github.com/rgrishman/ice.git clone-dir
cp jet-all.jar clone-dir/lib
pushd clone-dir
git checkout newmaster
ant dist-all-jar
popd
cp clone-dir/ice-all.jar .
perl Markdown_1.0.1/Markdown.pl clone-dir/README.md > README.html
perl Markdown_1.0.1/Markdown.pl clone-dir/docs/iceman.md > docs/iceman.html
perl Markdown_1.0.1/Markdown.pl clone-dir/docs/ICE_Design.md > docs/ICE_Design.html
cp clone-dir/docs/*.png docs/
cp clone-dir/LICENSE ./
cp clone-dir/COPYRIGHT ./
#  scripts
cp clone-dir/src/scripts/runice.sh ./bin
cp clone-dir/src/scripts/runtagger.sh ./bin
cp clone-dir/src/scripts/icecli ./bin
cp clone-dir/src/scripts/icecli6 ./bin
#  ice.yml, iceprops, onomaprops, parseprops, props
cp clone-dir/src/props/* ./
#  quantifierPatterns and ACE DTD
cp clone-dir/src/models/data/* ./data/
#  files for export from ICE
touch acedata/ice_onoma.dict
touch acedata/EDTypesFromUser.dict
touch acedata/iceRelationModel
chmod u+x bin/runice.sh
chmod u+x bin/runtagger.sh
chmod u+x bin/icecli6
chmod u+x bin/icecli
rm -rf Markdown*
rm -rf clone-dir
cd ..
echo "Building ICE tar"
set date = `date +'%y%m%d'`
tar zcvf ice-$date.tar.gz ice-bin/
