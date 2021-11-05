# Javen Reactor
*Lightweight support library for integration made by java developers*

## What is Javen reactor?
Javen Reactor is a small utility allowing to have intuitive ways to build a maven project without having to enter in the gradle or bash or maven profiles complexity.
It takes the best advantages of maven (dependency resolution, powerful plugin declarations) and 
combine them with a small orchestration program written in java.
The advantages are the following:
- developers can find their common reflex and apis to build their project faster through a CI
- The build becomes a dedicated piece of software and thus, can be tested, piece by piece, as any other software
- This approach allows reducing time in trials/errors cycles, it can indeed take a long time before converging to a succesful build.


## How to use Javen reactor?

Recommended: create a special module for integration called "${your-project}-integration", in which you'll place all the construction instructions.

first, you need to import the javen-reactor dependency in your pom.xml file

````xml
<dependency>
    <groupId>org.javen.integration</groupId>
    <artifactId>javen-reactor</artifactId>
    <version>1.0.0</version>
</dependency>
````
If you don't want to install it, just copy-paste the Reactor.java file in your project (it is recommended anyway to have a dedicated java project to realize the integrations tasks)

Then, in your project, call the Reactor apis to build your project.

use the api `Reactor.mvn.project("relative.path")` to declare a project, then call APIs on this project

````java
import static org.javen.integration.Reactor.*;

public class Integration {
	
	public static void main(String[] args){
		buildModules();
    }
	
	private static void buildModules() {
		listOf(
				mvn.project("module1"),
				mvn.project("module2")
		).forEach(
				project -> {
					logInfo("clean & package projects");
					project.prepareCleanInstall()
							.forceUpdate()
							.skipTests()
							.execute("building project");

					project.prepareCopyDependencies().execute("getting dependencies");
					project.prepareJavadoc()
							.userProperty("doclint", "none")
							.execute("generating javadoc");
					project.prepareSourcesJar().execute("generating sources");
				}
		);
	}
}
````
you can have a look to the sampleIntegrationProject and sampleProject subdirectories to find a sample operational configuration.

