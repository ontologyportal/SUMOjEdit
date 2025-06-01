# SUMOjEdit PLUGIN Introduction

Started with the QuickNotepad tutorial and adapting bit by bit as a syntax checker for [SUMO](https://www.ontologyportal.org)

# Installation Instructions
## Container-based installation (Docker)
### For WIN
- Install [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install)
- Configure an [X11 Server](https://github.com/saiccoumar/PX4_Docker_Config/#x11-forwarding)
### For *nix
- Install [Docker Desktop](https://docs.docker.com/desktop/setup/install/linux/ubuntu/)
- An X11 Server is already configured on *nix installations
### For macOS
- Install [Docker Desktop](https://docs.docker.com/desktop/setup/install/mac-install/)
```sh
brew install --cask --no-quarantine docker
```
- Install and configure [XQuartz](https://www.xquartz.org)
```sh
brew install --cask --no-quarantine xquartz
defaults write org.xquartz.X11 nolisten_tcp -bool false
defaults write org.xquartz.X11 no_auth -bool false
defaults write org.xquartz.X11 enable_iglx -bool true
```
In XQuartz's preferences, ensure that "Allow connections from network clients"\
is enabled.\
If Windows have a black background instead of white when they are focused\
Run the following, then restart XQuartz:
```sh
defaults write org.xquartz.X11 enable_render_extension -bool false
```
### Docker Desktop
- From the Docker Hub tab search for sumojedit, select the link, then Run which\
  will pull the image down. A 'Run a new container' dialog will appear. Under
  Ports (Host port) - type 8080 and Run. The container page will show Tomcat \
  startup on the Logs tab
- To use SUMOjEdit, from the Exec tab of the running container:
```sh
bash
jedit
```
- To run the SigmaKEE KB Browser, point your local browser to:
```url
http://localhost:8080/sigma/login.html
```
- Login with user:password -> admin:admin
- Wait a minute for the enviroment to set up the KB for first time use.\
  You can monitor KB initialization on the Container's Logs tab.
- When SigmaKEE's Home page appears, navigate to the Browse page and type in a \
  KB Term to search
### For CLI from your respective O/S command shell:
- Pull and run the sumojedit image:
```sh
docker pull apease/sumojedit:latest
docker run -it -d -p 8080:8080 --name test01 apease/sumojedit:latest
```
- To run the SigmaKEE KB Browser, point your local browser to:
```url
http://localhost:8080/sigma/login.html
```
- Login with user:password -> admin:admin
- Wait a minute for the enviroment to set up the KB for the first time use
- Navigate to the Browse page and type in a KB Term to search
- To use SUMOjEdit, first determine the running container id:
```sh
docker ps
```
- Then login to a Bash instance on the running sumojedit container and \
  execute jedit:
```sh
docker exec -it <container_id> /bin/bash
jedit
```
### Trouble shooting running a UI in a Docker container
- A lot of helpful info [here](https://medium.com/@saicoumar/running-linux-guis-in-a-docker-container-73fef186db30)

## Local installation
This depends on proper installation/building of [SigmaKEE](https://github.com/ontologyportal/sigmakee).

### For WIN
- Install [WSL2](https://github.com/ontologyportal/sigmakee/wiki/Windows-installation)
- Then follow the instructions for *nix below
### For *nix
=============================
- You are free to use OpenJDK. Latest is JDK23
- Install jEdit. On Ubuntu this is:
```sh
sudo apt-get install jedit
```
- Install SigmaKEE as per the [README](https://github.com/ontologyportal/sigmakee/blob/master/README.md)
- Clone SUMOjEdit and SigmaAntlr into your workspace directory:
```sh
cd ~/workspace
git clone https://github.com/ontologyportal/SUMOjEdit.git
git clone https://github.com/ontologyportal/sigmaAntlr.git
```
- Add to your ~/.bashrc editing "myname" to conform to your machine's home.
```sh
cd $HOME
echo "# SUMOjEdit" >> .bashrc
echo "export JEDIT_HOME=/home/myname/.jedit" >> .bashrc
echo "export JEDIT_JAR=/usr/share/jedit/jedit.jar" >> .bashrc
echo "alias jedit=\"java -Xmx10g -Xss1m -jar \$JEDIT_JAR\"" >> .bashrc
```
- This can also be performed by executing the following on the command line in\
  the SUMOjEdit directory.
```sh
cd ~/workspace/SUMOjEdit
ant append.bashrc
```
- Then build SUMOjEdit which deploys to your jedit home
```sh
source ~/.bashrc
ant
```
- If you haven't yet run sigmakee for the first time, then it's recommended\
  to build the KB with this command:
```sh
java -Xmx10g -Xss1m -cp $SIGMA_CP com.articulate.sigma.KB -l -R
```
- If you want to monitor SUMOjEdit's condition to see if it started\
  successfully you can run:
```sh
tail -f $JEDIT_HOME/activity.log
```
- Next, to start SUMOjEdit execute:
```sh
jedit
```

### For macOS
=============================
- Basically mirrors the above install notes with these differences
- Install [jEdit](http://jedit.org/index.php?page=download&platform=mac)
  (Choose the Mac OS X package link)\
  After installing, you may need to go to System->Security&Privacy->Security and allow the jedit app
- Note that your jEdit home directory on macOS is: /Users/myname/Library/jEdit or ~/Library/jEdit\
  Contents of your ~/.zshrc:
```sh
cd $HOME
echo "# SUMOjEdit" >> .zshrc
echo "export JEDIT_HOME=~/Library/jEdit" >> .zshrc
echo "export JEDIT_JAR=/Applications/jEdit.app/Contents/Java/jedit.jar" >> .zshrc
echo "alias jedit=\"java -Xmx10g -Xss1m -jar \$JEDIT_JAR\"" >> .zshrc
```
Or just execute:
```sh
cd ~/workspace/SUMOjEdit
ant append.zshrc
source ~/.zshrc
ant
```
## Installation Issues
=============================\
If you don't get syntax highlighting or the SUMOjEdit plugin menu, the following\
may help.

To get the syntax highlighting, add these modes through the gui menu via \
Utilities->Global Options->Editing and the Edit Modes tab

Use the Add Mode tab to

- Name a mode SUMO
- With Select Mode file to be $JEDIT_HOME/modes/kif.xml
- And File Name Glob *.kif

- Name a mode TPTP
- With Select Mode file to be $JEDIT_HOME/modes/TPTP.xml
- And File Name Glob *.tptp

## Execution
=============================
- startup jEdit and wait a moment since it loads all the kif files specified in your Sigma \
  config.xml, all of WordNet, VerbNet, etc. You may begin to use the editor even though\
  plugin features will be disabled until this finishes.
- load a .kif or .tptp file
- go to Plugins->SUMOjEdit Plugin->check for SUO-KIF errors
- you can also highlight a SUO-KIF expression, then Plugins->SUMOjEdit Plugin->query on highlighted expression.\
  If you have Vampire installed and it finds a proof, a new buffer will be opened to display the proof.\
  It should work with Eprover too, but needs testing.
- other functions are "format axioms" which will reformat a highlighted axiom with standard SUMO indentation.\
  "go to definition" will make a guess at where the definition for a selected term starts and put the cursor \
  at that point in the file.\
  "browse term in Sigma" will open the public Sigma site in your browser opened on the selected term.
- Activity log - it's good to monitor SUMOjEdit from jEdit's activity log by selecting Utilities->Troubleshooting->Activity Log.\
  When the dialog is open, select tail to get continuous updates. Dock the dialog to the base area of jEdit to monitor continuously.

## Customization
=============================
- you may wish to customize by right clicking in the editor and selecting "Customize This Menu,"\
select the "Context Menu" under jEdit, then the '+' symbol. First, add a seperator, then select\
"Plugin: SUMOjEdit Plugin" from the Command or macro: dialog menu and add "format axioms",\
"go to definition" and "query on highlighted expressions", which are handy to have on the context\
menu as well as the main plugin menu. In later versions this may already be performed as part of\
the configuration, but for now you'll need to add the menu items manually.
- select the ErrorList from Plugins and dock the dialog to the base of jEdit.\
From the Plugin Manager, select ErrorList and select all available features.
- ![screenshot](https://github.com/ontologyportal/SUMOjEdit/raw/master/screenshot-errorlist-options.png)
- install the EditorScheme plugin with Plugins->PluginManager->Install-EditorScheme then\
Plugins-EditorScheme->SchemeSelector. I like the Neon theme as the best "Dark mode" option
- if you have a small screen or imperfect eyes you may wish to adjust the font size with\
Utilities->GlobalOptions->jEdit->TextArea->TextFont

## To build/run/debug/test on macOS using the NetBeans IDE
=============================\
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

![screenshot](https://github.com/ontologyportal/SUMOjEdit/raw/master/screenshot.jpeg)
![screenshot](https://github.com/ontologyportal/SUMOjEdit/raw/master/screenshot-tp.jpeg)
