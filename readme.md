MetaGen is an annotation processor that generates metadata about classes.

#### Features
Currently it only generates property meta information that can be used to construct
refactor safe property expressions.

For example, using metagen-wicket module the following code that depends on strings:

    IModel<Person> person=getPersonModel();
    IModel<String> street=new PropertyModel<Street>(person, "address.street");

can be replaced with the following:

    IModel<Person> person=getPersonModel();
    IModel<String> street=MetaModel.of(person).get(PersonMeta.address).get(AddressMeta.street);

although the code is a more verbose then its string alternative it will generate
a compile time error should any properties change instead of failing at runtime
like its more concise string alternative.

#### Triggers
The processor supports the following triggers that will generate meta classes:
* A class is annotated with a JPA annotaiton, eg @Entity or @MappedSuperClass
* A class is annotated with @Bean annotation
* A field or getter is annotated with @Property annotation

The annotation processor is still in early stages of development

#### Installation
Add the following dependencies into your pom.xml

	<dependency>
		<groupId>net.ftlines.metagen</groupId>
		<artifactId>metagen-core</artifactId>
		<version>${metagen.version}</version>
	</dependency>
	<dependency>
		<groupId>net.ftlines.metagen</groupId>
		<artifactId>metagen-processor</artifactId>
		<version>${metagen.version}</version>
	</dependency>
	<dependency>
		<groupId>net.ftlines.metagen</groupId>
		<artifactId>metagen-wicket</artifactId>
		<version>${metagen.version}</version>
	</dependency>

See here for available versions: http://search.maven.org/#search%7Cga%7C1%7Cnet.ftlines.metagen
	
Add the following to your build plugins in your pom.xml

    <!-- disable default annotation processing by javac -->
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
            <compilerArgument>-proc:none</compilerArgument>
       </configuration>
    </plugin>
    <!-- use maven-processor to run apt processors -->
    <plugin>
        <groupId>org.bsc.maven</groupId>
        <artifactId>maven-processor-plugin</artifactId>
        <version>2.0.5</version>
        <executions>
            <execution>
                <id>process</id>
                <goals>
                    <goal>process</goal>
                </goals>
                <phase>generate-sources</phase>
                <configuration>
                    <outputDirectory>target/metamodel</outputDirectory>
                </configuration>
            </execution>
        </executions>
    </plugin>
    <!-- make sure target/metamodel is on the source path -->
    <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>build-helper-maven-plugin</artifactId>
        <version>1.3</version>
        <executions>
            <execution>
                <id>add-source</id>
                <phase>generate-sources</phase>
                <goals>
                    <goal>add-source</goal>
                </goals>
                <configuration>
                    <sources>
                        <source>target/metamodel</source>
                    </sources>
                </configuration>
            </execution>
        </executions>
    </plugin>
You may also need to add a plugin repository to get the 2.0.5 version of the maven processor:

    <pluginRepository>
        <id>sonatype-repo</id>
        <url>https://oss.sonatype.org/content/repositories/releases</url>
    </pluginRepository>

#### Building MetaGen from source
Because this is an annotation processor there are some hoops to jump through compared to other projects. Namely, the project has to be installed like this:
    mvn install -Dmaven.test.skip=true
After the above command has been executed normal commands can be used, such as
    mvn eclipse:eclipse

