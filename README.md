SUMOjEdit PLUGIN

![screenshot](https://github.com/ontologyportal/SUMOjEdit/raw/master/screenshot.jpeg)

Started with the QuickNotepad tutorial and adapting bit by bit as a syntax checker for SUMO
www.ontologyportal.org

This depends on SigmaKEE https://github.com/ontologyportal/sigmakee and also the jEdit ErrorList plugin.

Installation-*nix
=============================
- install jEdit.  On Ubuntu this is "sumo apt-get install jedit"
- Install SigmaKEE as per the README at https://github.com/ontologyportal/sigmakee
- create the directory /home/myname/workspace
- clone SUMOjEdit into your workspace directory
- edit build.xml to conform to your paths
- make sure you don't already have a "catalog" file in your ~/.jedit/modes directory, 
or if you do, append the contents of ~/workspace/SUMOjEdit/catalog to it
- then execute "ant" from the top SUMOjEdit directory

Installation-Mac
=============================
- You may need to use Oracle Java rather than OpenJDK, if you get a "JRE Load Error"
- install jEdit from http://jedit.org/index.php?page=download&platform=mac
- you may need to go to System->Security&Privacy->General and allow this app
- create the directory /Users/myname/workspace
- Install SigmaKEE as per the README at https://github.com/ontologyportal/sigmakee
- clone SUMOjEdit into your workspace directory
- edit build.xml to conform to your paths - note that your jEdit config directory on a 
  mac is /Users/myname/Library/jEdit
- make sure you don't already have a "catalog" file in your 
/Users/myname/Library/jEdit/modes directory, 
  or if you do, append the contents of ~/workspace/SUMOjEdit/catalog to it
- then execute "ant" from the top SUMOjEdit directory
- you may have to start jEdit from the command line to get it to use the correct java with
  java -jar /Applications/jedit/Contents/Java/jedit.jar

Execution
=============================
- startup jEdit (and wait a while since it loads all the kif files specified in your Sigma config.xml,
  all of WordNet, VerbNet etc)
- load a .kif file
- go to Plugins->SUMOjEdit Plugin->check KIF syntax errors
