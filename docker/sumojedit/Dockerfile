# A lot of good help from: https://medium.com/@saicoumar/running-linux-guis-in-a-docker-container-73fef186db30
ARG IMAGE_ACCOUNT=apease
FROM $IMAGE_ACCOUNT/sigmakee:latest AS builder

#################################################
# runtime image.
FROM tomcat:9.0.97-jdk21-temurin-noble AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends \
    graphviz \
    x11-apps \
    jedit

ENV SIGMA_HOME=/root/sigmakee-runtime
ENV LD_LIBRARY_PATH=/usr/local/bin:$LD_LIBRARY_PATH
ENV CATALINA_OPTS="-Xmx10g -Xss1m"
ENV JEDIT_HOME=~/.jedit
ENV JEDIT_JAR=/usr/share/jedit/jedit.jar
ENV DISPLAY=host.docker.internal:0

# We want the alias to start jedit vice the installed script
RUN rm -f /usr/bin/jedit
RUN echo "" >> ~/.bashrc
RUN echo "# SUMOjEDIT" >> ~/.bashrc
RUN echo 'alias jedit="java -Xmx10g -Xss1m -jar $JEDIT_JAR"' >> ~/.bashrc

COPY --from=builder \
    /usr/local/bin/e_ltb_runner /usr/local/bin/e_ltb_runner
COPY --from=builder \
    /usr/local/bin/vampire /usr/local/bin/vampire
COPY --from=builder \
    /usr/local/bin/libz3.* /usr/local/bin/
COPY --from=builder \
    $CATALINA_HOME/webapps/sigma.war $CATALINA_HOME/webapps/sigma.war

# Just the essentials
COPY --from=builder \
    $SIGMA_HOME/KBs/config.xml $SIGMA_HOME/KBs/
COPY  --from=builder \
    $SIGMA_HOME/KBs/*.kif $SIGMA_HOME/KBs/
COPY  --from=builder \
    $SIGMA_HOME/KBs/Translations $SIGMA_HOME/KBs/Translations/
COPY  --from=builder \
    $SIGMA_HOME/KBs/WordNetMappings $SIGMA_HOME/KBs/WordNetMappings/
COPY  --from=builder \
    $SIGMA_HOME/KBs/development $SIGMA_HOME/KBs/development/

COPY ./sumojedit/lib/*.jar /root/sumojedit/
COPY ./sumojedit/SUMOjEdit.jar /root/sumojedit/
ADD .jedit /root/.jedit
