---
layout: default
title: Getting Started
---


{% assign version = '1.1.0' %}


# Getting Started

The Eve agent platform can be easily integrated in an existing Java project
useing the provided libraries. 
Eve provides various libaries to deploy agents on different platforms, 
with different storage facilities.
The agents can for example be deployed on a regular Tomcat server that you host
yourself, or on Google App Engine in the cloud.

This tutorial shows how to create a an application with your own agent, and 
deploy it on Google App Engine, as it is easy to get started with Google 
App Engine, and it is easy to really deploy your project into the cloud.
Googles Datastore is used for persistency.
Creating a project for another type of deployment is similar, you basically
just have to include the Eve libraries and configure a web servlet.

The tutorial contains the following steps:

- [Prerequisites](#prerequisites)
- [Project Setup](#project_setup)
- [Usage](#usage)
- [Create your own agent](#create_your_own_agent)
- [Deployment](#deployment)


## Prerequisites {#prerequisites}

This tutorial uses Eclipse and the Google Web Toolkit plugin.

- Download and unzip [Eclipse Helios (3.6)](http://www.eclipse.org/helios/).
  On the site, click Download Helios, Eclipse IDE for Java Developers.
  Then select the correct zip file for your system and download it.
  Unzip it somewhere on your computer and start Eclipse.
  Note that you need to have a Java SDK installed on your computer.

- In Eclipse install the [Google Web Toolkit](http://code.google.com/webtoolkit/) plugin. 
  Go to menu Help, Install New Software... Click Add to add a new software source,
  and enter name "Google Web Toolkit" and location 
  [http://dl.google.com/eclipse/plugin/3.6](http://dl.google.com/eclipse/plugin/3.6).
  Click Ok. 
  Then, select and install "Google Plugin for Eclipse" and "SDKs".

Note that for a typical java web application you would need the 
[Web Tools Platform plugin](http://download.eclipse.org/webtools/repository/helios/) 
and a [Tomcat server](http://tomcat.apache.org/).


## Project Setup {#project_setup}

We will create a new project, add the required libraries, and configure a
web servlet.

- Create a new GWT project in Eclipse via menu New, Project, Google,
  Web Application Project. Select a project name and a package name, 
  for example "MyEveProject" and "com.mycompany.myproject".
  Unselect the option "Use Google Web Toolkit", and select the options 
  "Use Google App Engine" and "Generate GWT project sample code" checked. 
  Click Finish.

- Download the following jar files, and put them in your Eclipse project
  in the folder war/WEB-INF/lib. 

  - [eve-core-{{version}}.jar](http://search.maven.org/remotecontent?filepath=com/almende/eve/eve-core/{{version}}/eve-core-{{version}}.jar)

    - [commons-codec-1.6.jar](http://commons.apache.org/proper/commons-codec/)
    - [commons-logging-1.1.1.jar](http://commons.apache.org/proper/commons-logging/)
    - [httpclient-4.2.3.jar](http://hc.apache.org/downloads.cgi)
    - [httpcore-4.2.2.jar](http://hc.apache.org/downloads.cgi)
    - [jackson-databind-2.0.0.jar](http://jackson.codehaus.org)
    - [jackson-core-2.0.0.jar](http://jackson.codehaus.org)
    - [jackson-annotations-2.0.0.jar](http://jackson.codehaus.org)
    - [joda-time-2.1.jar](http://joda-time.sourceforge.net/)
    - [smack-3.1.0.jar](http://www.igniterealtime.org/projects/smack/)
      (optional, only needed for XMPP support)
    - [smackx-3.1.0.jar](http://www.igniterealtime.org/projects/smack/)
      (optional, only needed for XMPP support)
    - [snakeyaml-1.11.jar](http://snakeyaml.org)

  - [eve-gae-{{version}}.jar](http://search.maven.org/remotecontent?filepath=com/almende/eve/eve-gae/{{version}}/eve-gae-{{version}}.jar)
  
    - [twig-persist-2.0-rc.jar](http://code.google.com/p/twig-persist)
    - [guava-10.0.jar](http://code.google.com/p/guava-libraries)
    - [guice-3.0.jar](https://code.google.com/p/google-guice/wiki/Guice30)

  In stead of downloading all libraries individually, it is easier to create a
  maven project and add eve-gae as dependency. This will automatically resolve
  all dependencies. See [Downloads](java_downloads.html).
  
- Right-click the added jars in Eclipse, and click Build Path, "Add to Build Path". 

- Now, you need to configure a web-servlet which will host your agents.
  Open the file web.xml under war/WEB-INF. Insert the following lines
  inside the &lt;web-app&gt; tag:

      <context-param>
          <description>eve configuration (yaml file)</description>
          <param-name>config</param-name>
          <param-value>eve.yaml</param-value>
      </context-param>
      <listener>
          <listener-class>com.almende.eve.transport.http.AgentListener</listener-class>
      </listener>

      <servlet>
          <servlet-name>AgentServlet</servlet-name>
          <servlet-class>com.almende.eve.transport.http.AgentServlet</servlet-class>
          <init-param>
              <param-name>environment.Development.servlet_url</param-name>
              <param-value>http://localhost:8888/agents/</param-value>
          </init-param>
          <init-param>
              <param-name>environment.Production.servlet_url</param-name>
              <param-value>http://myeveproject.appspot.com/agents/</param-value>
          </init-param>
      </servlet>
      <servlet-mapping>
          <servlet-name>AgentServlet</servlet-name>
          <url-pattern>/agents/*</url-pattern>
      </servlet-mapping>

  Note that we have added a number of init parameters.
  The context-param `config` points to an eve configuration file eve.yaml,
  which we will create next. This configuration file is used to load an agent
  factory which manages all agents.
  Furthermore, the servlet needs a parameter `servlet_url`. This url is needed
  in order to be able to built an agents full url.
  The `servlet_url` parameter can be defined separately for different
  environments, hence the parameters
  `environment.Development.servlet_url` and `environment.Production.servlet_url`.

- Create an Eve configuration file named eve.yaml in the folder war/WEB-INF 
  (where web.xml is located too). Insert the following text in this file:
  
      # Eve configuration

      # communication services
      # services:
      # - class: ...

      # state settings (for persistence)
      state:
        class: DatastoreStateFactory

      # scheduler settings (for tasks)
      scheduler:
        class: AppEngineSchedulerFactory

  The configuration is a [YAML](http://en.wikipedia.org/wiki/YAML) file.
  It contains:

  - Parameters *services*. This parameter is not needed in our case, as the
    configured servlet will register itself as communication service for the
    agents.
    The parameter *services* allows configuration of multiple communication
    services such ass HTTP or XMPP, which enables agents to communicate with
    each other in multiple ways.
    An agent will have a unique url for each of the configured services.

  - The parameter *state* specifies the type of state that will be
    available for the agents to read and write persistent data.
    Agents themselves are stateless. They can use a state to persist data.

  - The parameter *scheduler* specifies the scheduler that will be used to
    let agents schedule tasks for themselves.

  - Optionally, all Eve parameters can be defined for a specific environment:
    Development or Production. In that case, the concerning parameters
    can be defined under `environment.Development.[param]` and/or
    `environment.Production.[param]`.

  Each agent has access has access to this configuration file via its 
  AgentFactory.
  If your agent needs specific settings (for example for database access), 
  you can add these settings to the configuration file.

  More detailed information on the Eve configuration can be found on the page
  [Configuration](java_configuration.html).


## Usage {#usage}

Now the project can be started and you can see one of the example agents in action.

- Start the project via menu Run, Run As, Web Application.
  
- To verify if the AgentServlet of Eve is running, open your browser and
  go to http://localhost:8888/agents/.
  This will return generic information explaining the usage of the servlet.
  Agents can be created by sending a HTTP PUT request to the servlet, deleted
  using a HTTP DELETE request, and invoked via an HTTP POST request.
  To execute HTTP requests you can use a REST client like
  [Postman](https://chrome.google.com/webstore/detail/fdmmgilgnpjigdojojpjoooidkmcomcm) in Chrome,
  [RESTClient](https://addons.mozilla.org/en-US/firefox/addon/restclient/?src=search) in Firefox,
  or with a tool like [cURL](http://curl.haxx.se/).

- Create a CalcAgent by sending an HTTP PUT request to the servlet. We will
  create an agent with id `calcagent1` and class `com.almende.eve.agent.example.CalcAgent`.

      http://localhost:8888/agents/calcagent1/?type=com.almende.eve.agent.example.CalcAgent

  If the agent is successfully created, the agents urls will be returned
  (in this case only one url):

      http://localhost:8888/agents/calcagent1/

  Note that when an agent with this id already exists, the request will return
  a server error.

- Agents can be invoked via an HTTP POST request. The url defines
  the location of the agent and looks like 
  http://server/servlet/{agentId}.
  The body of the POST request must contain a JSON-RPC message.

  Perform an HTTP POST request to the CalcAgent on the url

      http://localhost:8888/agents/calcagent1/

  With request body:

      {
          "id": 1,
          "method": "eval",
          "params": {
              "expr": "2.5 + 3 / sqrt(16)"
          }
      }
  
  This request will return the following response:
  
      {
          "jsonrpc": "2.0",
          "id": 1,
          "result": "3.25"
      }


## Create your own agent {#create_your_own_agent}

Now, what you want of course is create your own agents. This is quite easy:
create a java class which extends from the base class Agent, and register
your agent class in the eve.properties file.


- Create a new java class named MyFirstAgent under com.mycompany.myproject 
  with the following contents:
  
      package com.mycompany.myproject;
      
      import com.almende.eve.agent.Agent;
      import com.almende.eve.agent.annotation.Name;

      public class MyFirstAgent extends Agent {
          public String echo (@Name("message") String message) {
              return message;  
          }
          
          public double add (@Name("a") double a, @Name("b") double b) {
              return a + b;  
          }
          
          @Override
          public String getDescription() {
              return "My first agent";
          }
          
          @Override
          public String getVersion() {
              return "0.1";
          }
      }

  
  Each agent must contain at least two default methods: getDescription 
  and getVersion. Next, you can add your own methods, in this example the 
  methods echo and add. 

- Create an instance of your new agent. Send an HTTP PUT request to the servlet.
  We will create an agent with id `myfirstagent1` and class `com.mycompany.myproject.MyFirstAgent`.

      http://localhost:8888/agents/myfirstagent1/?type=com.mycompany.myproject.MyFirstAgent

  If the agent is successfully created, its urls will be returned:

      http://localhost:8888/agents/myfirstagent1/

- Now you can perform an HTTP POST request to the new agent
  
      http://localhost:8888/agents/myfirstagent1/
  
  With as request:
  
      {
          "id": 1,
          "method": "echo",
          "params": {
              "message": "Hello World"
          }
      }
  
  which returns:

      {
          "jsonrpc": "2.0",
          "id": 1,
          "result": "Hello World"
      }

  or send the following request:
  
      {
          "id": 1,
          "method": "add",
           "params": {
                "a": 2.1,
                "b": 3.5
           }
      }

  which returns:

      {
           "jsonrpc": "2.0",
           "id": 1,
           "result": 5.6
      }


## Deployment {#deployment}

Now you can deploy your application in the cloud, to Google App Engine.

- Register an application in appengine.
  In your browser, go to [https://appengine.google.com](https://appengine.google.com).
  You will need a Google account for that. Create a new application by clicking
  Create Application. Enter an identifier, for example "myeveproject" and a 
  title and click Create Application.

- In Eclipse, go to menu Project, Properties. Go to the page Google, App Engine.
  Under *Deployment*, enter the identifier "myeveproject" of your application 
  that you have just created on the appengine site. Set version to 1. Click Ok.
  
- Ensure the servlet_url for the production environment in the configuration
  file war/WEB-INF/web.xml corresponds with your application
  identifier: http://myeveproject.appspot.com/agents/
  
      ...
        <init-param>
          <param-name>environment.Development.servlet_url</param-name>
          <param-value>http://localhost:8888/agents/</param-value>
        </init-param>
        <init-param>
          <param-name>environment.Production.servlet_url</param-name>
          <param-value>http://myeveproject.appspot.com/agents/</param-value>
        </init-param>
      ...

- In Eclipse, right-click your project in the Package Explorer. In the context
  menu, choose Google, Deploy to App Engine. Click Deploy in the opened window,
  and wait until the deployment is finished.
  
- Your application is now up and running and can be found at 
  http://myeveproject.appspot.com (where you have to replace the identifier with 
  your own). The servlet to create, delete, and invoke agents is available at
  http://myeveproject.appspot.com/agents/.

