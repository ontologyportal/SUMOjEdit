SUMOjEdit PLUGIN

Started with the QuickNotepad tutorial and adapting bit by bit as a syntax checker for SUMO
www.ontologyportal.org

This depends on SigmaKEE https://github.com/ontologyportal/sigmakee and also the jEdit ErrorList plugin.

Installation
=============================
- edit build.xml to conform to your paths
- Install SigmaKEE as per the README at https://github.com/ontologyportal/sigmakee
- install jEdit.  On Ubuntu this is "sumo apt-get install jedit"
- clone SUMOjEdit, then execute "ant" from the top SUMOjEdit directory

Execution
=============================
- startup jEdit (and wait a while since it loads all the kif files specified in your Sigma config.xml,
  all of WordNet, VerbNet etc)
- load a .kif file
- go to Plugins->SUMOjEdit Plugin->check KIF syntax errors
