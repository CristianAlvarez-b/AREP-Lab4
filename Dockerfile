FROM openjdk:17

WORKDIR /usrapp/bin

ENV PORT 6000

COPY /target/classes /usrapp/bin/classes
COPY /target/dependency /usrapp/bin/dependency
COPY src/main/resources/webroot/public /usrapp/bin/public

CMD ["java","-cp","./classes:./dependency/*","co.edu.eci.arep.serverHttp.WebApplication"]
