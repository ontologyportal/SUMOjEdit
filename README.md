SUMOjEdit PLUGIN

![screenshot](https://github.com/ontologyportal/SUMOjEdit/raw/master/screenshot.jpeg)
![screenshot](https://github.com/ontologyportal/SUMOjEdit/raw/master/screenshot-tp.jpeg)
Started with the QuickNotepad tutorial and adapting bit by bit as a syntax checker for [SUMO](www.ontologyportal.org)

This depends on proper installation/building of [SigmaKEE](https://github.com/ontologyportal/sigmakee).

Installation-*nix
=============================
- You are free to use OpenJDK. Latest is JDK23
- install jEdit.  On Ubuntu this is "sudo apt-get install jedit"
- Install SigmaKEE as per the [README](https://github.com/ontologyportal/sigmakee/blob/master/README.md)
- clone SUMOjEdit into your workspace directory
- edit build.xml to conform to your paths
- add to your .bashrc\
  export JEDIT_HOME=/home/myname/.jedit editing "myname" to conform to your machine home\
  can be performed by: ant append.bashrc on the command line in the top SUMOjEdit directory
- make sure you don't already have a "catalog" file in your ~/.jedit/modes\
  directory, or if you do, append the contents of ~/workspace/SUMOjEdit/catalog\
  to it
- Start jEdit normally so that it creates its ${jedit.home} space, then execute "ant" from the top SUMOjEdit directory
- On the command line execute: java -Xmx10g -Xss1m -jar /usr/share/jedit/jedit.jar\
  Can also create a .bashrc alias for the above java command\
  alias jedit="java -Xmx10g -Xss1m -jar /usr/share/jedit/jedit.jar"

Installation-Mac
=============================
- You are free to use OpenJDK. Latest is JDK23
- install [jEdit](http://jedit.org/index.php?page=download&platform=mac)
  (Choose the Mac OS X package link)\
  After installing, you may need to go to System->Security&Privacy->Security and allow the jedit app
- Install SigmaKEE as per the [README](https://github.com/ontologyportal/sigmakee/blob/master/README.md)
- clone SUMOjEdit into your workspace directory
- edit build.xml to conform to your paths - note that your jEdit config\
  directory on a mac is /Users/myname/Library/jEdit
- add to your \~/.zshrc\
  export JEDIT_HOME=\~/Library/jEdit\
  can be performed by: ant append.zshrc on the command line in the top SUMOjEdit directory
- make sure you don't already have a "catalog" file in your\
  /Users/myname/Library/jEdit/modes directory, or if you do, append the contents\
  of ~/workspace/SUMOjEdit/catalog into it
- Start jEdit normally so that it creates its ${jedit.home} space, then execute "ant" from the top SUMOjEdit directory
- On the command line execute: java -Xmx10g -Xss1m -jar /Applications/jEdit.app/Contents/Java/jedit.jar\
  Can also create a .zshrc alias to perfrom the above java command\
  alias jedit="java -Xmx10g -Xss1m -jar /Applications/jEdit.app/Contents/Java/jedit.jar"

To build/run/debug/test on macOS using the NetBeans IDE
=======================================================
Define a nbproject/private/private.properties file with these keys:

\# private properties\
javaapis.dir=${user.home}/javaapis\
workspace=${javaapis.dir}/INSAFE

\# The default installation space is: ~/workspace. However, it can be anywhere on\
\# your system as long as you define the "workspace" key above.

catalina.home=${path.to.your.tomcat9}

jedit.home=${user.home}/Library/jEdit\
jedit.jar=/Applications/jEdit.app/Contents/Java/jedit.jar

\# JavaMail properties\
user=${your.email.user.name}\
my.email=${user}@${your.email.domain}\
my.name=${your.name}


Installation Issues
=============================
If you don't get syntax highlighting and the SUMO plugin menu, the following
may help.

To get the syntax highlighting, add the mode through the gui Global Options->Editing and the Edit Modes tab

Use the Add mode tab to

- Name a mode SUMO
- With Select Mode file to be /home/&lt;user&gt;/workspace/SUMOjEdit/kif.xml
- And File Name Glob *.kif

The plugins then should show up on the menu, but you may also need to launch jedit from the SUMOjEdit directory.

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
