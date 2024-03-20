#./mvnw -N jacoco:merge -Pcodecover
#mv target/jacoco/jacoco.exec target
#./mvnw jacoco:report -Djacoco.dataFile=target/jacoco.exec
./mvnw clean install -Pcodecover
./mvnw -N jacoco:merge -Pcodecover
#./mvnw jacoco:report -Djacoco.dataFile=target/jacoco.exec
