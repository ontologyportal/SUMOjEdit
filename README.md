SUMOjEdit PLUGIN

![screenshot](https://github.com/ontologyportal/SUMOjEdit/raw/master/screenshot.jpeg)
![screenshot](https://github.com/ontologyportal/SUMOjEdit/raw/master/screenshot-tp.jpeg)
Started with the QuickNotepad tutorial and adapting bit by bit as a syntax checker for SUMO
www.ontologyportal.org

This depends on SigmaKEE https://github.com/ontologyportal/sigmakee and also the jEdit ErrorList plugin.

Installation-*nix
=============================
- install jEdit.  On Ubuntu this is "sumo apt-get install jedit"
- add to your .bashrc 
  export JEDIT_HOME=/home/myname/.jedit editing the path to conform to your installation
- Install SigmaKEE as per the README at https://github.com/ontologyportal/sigmakee
- create the directory /home/myname/workspace where "myname" is the name of your home directory
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
- add to your .bash-profile 
  export JEDIT_HOME=/home/myname/.jedit editing the path to conform to your installation
- create the directory /Users/myname/workspace where "myname" is the name of your home directory
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
- you can also highlight a SUO-KIF expression and Plugins->SUMOjEdit Plugin->query on highlighted expression
  If you have Vampire installed and it finds a proof, a new buffer will be opened to display the proof.  It 
  should work with Eprover too but I need to test
- other functions are "format axioms" which will reformat a highlighted axiom with standard SUMO indentation.
  "go to definition" will make a guess at where the definition for a selected term starts and put the cursor at that
  point in the file. "Browse term in Sigma" will open the public Sigma site in your browser, open on the
  selected term.

Customization
=============================
- you may wish to right click in the editor and "customize this menu" select the "Context Menu"
ten the '+' symbol.  Select "Plugin: SUMOjEdit Plugin" from the dialog menu and add 
"format axioms" and "go to definition", which are handy to have on the context menu as well
as the main plugin menu.  In later versions I may learn how to do this as part of the configuration
but for now you'll need to add the menu items manually.
- install the EditorScheme plugin with Plugins->PluginManager->Install-EditorScheme then 
Plugins-EditorScheme->SchemeSelector .  I like the Neon theme as the best "Dark mode" option
- If you have a small screen or imperfect eyes you may wish to adjust the font size with 
Utilities->GlobalOptions->jEdit->TextArea->TextFont
