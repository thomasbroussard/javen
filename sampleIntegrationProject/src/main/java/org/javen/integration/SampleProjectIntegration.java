package org.javen.integration;

import static org.javen.integration.Reactor.*;

public class SampleProjectIntegration {


	public static void main(String[] args) {
		logInfo(System.getProperty("maven.home"));
		logInfo("this is a test build for javen");
		MvnProject project = mvn.project("../sampleProject");

		project.prepareCleanInstall()
				.execute("doing compilation and install");

		project.prepareJavadoc()
				.userProperty("doclint", "none")
				.execute("generating javadoc");

		project.prepareSourcesJar().execute("generating sources");

	}
}
