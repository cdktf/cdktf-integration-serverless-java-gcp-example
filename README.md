# CDK for Terraform Serverless Application in Java (Google Cloud Provider)

This repository contains an end to end serverless web app hosted on GCP and deployed with [CDK for Terraform](https://cdk.tf) in Java. In more application specific terms, we are deploying serverless infrastructure for a web app that has a list of posts and a modal to create a new post by specifying author and content. For more information regarding setup and the features of CDKTF [please refer to these docs](https://www.terraform.io/cdktf).

## Techstack

Frontend: React, Create React App, statically hosted via Google Cloud Storage
Backend API: GCP Cloud Function + Cloud SQL

## Application

### Stacks

We will have two primary Stacks– PostsStack and FrontendStack

The Post and Frontend class encapsulate the finer details of infrastructure provisioned for each respective Stack. The first parameter denotes the scope of the infrastructure being provision– we use `this` to tie the infrastructure contained in Post/Frontend to the Stack in which it is contained, the same is true with `GoogleBetaProvider`.

```Java
public static class PostsStack extends TerraformStack{

    private String httpsTriggerUrl;

    public PostsStack(Construct scope, String name, String environment, String user, String project){
        super(scope, name);

        new GoogleBetaProvider(this, "google-beta", GoogleBetaProviderConfig.builder()
                .region("us-east1")
                .project(project)
                .build()
        );

        Posts posts = new Posts(this, "posts-" + environment + "-" + user, environment, user, project);

        this.httpsTriggerUrl = posts.getHttpsTriggerUrl();
    }

    public String getHttpsTriggerUrl(){
        return this.httpsTriggerUrl;
    }
}
```

```java
public static class FrontendStack extends TerraformStack{

    public FrontendStack(Construct scope, String name, String environment, String user, String project, String httpTriggerUrl){
        super(scope,name);

        new GoogleBetaProvider(this, "google-beta", GoogleBetaProviderConfig.builder()
                .region("us-east1")
                .project(project)
                .build()
        );

        new GoogleComputeProjectDefaultNetworkTier(this, "network-tier", GoogleComputeProjectDefaultNetworkTierConfig.builder()
                .project(project)
                .networkTier("PREMIUM")
                .build()
        );

        new LocalProvider(this, "local");

        new Frontend(this, "frontend-" + environment + "-" + user, project, environment, user, httpTriggerUrl);

    }
}
```

PostsStack and FrontendStack are static nested classes within main.java, which is the entry point for all infrastructure definitions provided by `cdktf init --template=java`.

In using different Stacks to separate aspects of our infrastructure we allow for separation in state management of the frontend and backend– making alteration and redeployment of a specific piece of infrastructure a simpler process. Additionally, this allows for the instantiation of the same resource multiple times throughout.

For example…

```java
// In main.java

PostsStack postsDev = new PostsStack(app, "posts-dev", "development", System.getenv("CDKTF_USER"), System.getenv("PROJECT_ID"));
FrontendStack frontendDev = new FrontendStack(app, "frontend-dev", "development", System.getenv("CDKTF_USER"), System.getenv("PROJECT_ID"), postsDev.getHttpsTriggerUrl());

PostsStack postsProd = new PostsStack(app, "posts-prod", "production", System.getenv("CDKTF_USER"), System.getenv("PROJECT_ID"));
FrontendStack frontendProd = new FrontendStack(app, "frontend-prod", "production", System.getenv("CDKTF_USER"), System.getenv("PROJECT_ID"), postsProd.getHttpsTriggerUrl());
```

Here we created separate instances of the infrastructure for the frontend and backend with different naming of the resources in each application environment (by ways of the environment param), with the ease of adding additional as needed.

## Posts

The Posts class melds two elements together– the Cloud SQL DB and our Cloud Function that takes our new Cloud SQL DB for setting up the Cloud Function's environment.

```java
public class Posts extends Resource {

    private String httpsTriggerUrl;

    public Posts(Construct scope, String id, String environment, String user, String project){
        super(scope, id);

        GoogleComputeNetwork vpc = new GoogleComputeNetwork(this, "vpc-" + environment, GoogleComputeNetworkConfig.builder()
                .name("vpc-" + environment)
                .project(project)
                .autoCreateSubnetworks(false)
                .build()
        );

        GoogleComputeGlobalAddress privateIp = new GoogleComputeGlobalAddress(this, "internal-ip-address-" + environment + "-" + user, GoogleComputeGlobalAddressConfig.builder()
                .name("internal-ip-address-" + environment + "-" + user)
                .project(project)
                .purpose("VPC_PEERING")
                .addressType("INTERNAL")
                .prefixLength(16)
                .network(vpc.getId())
                .build()
        );

        GoogleServiceNetworkingConnection privateVpcConnection = new GoogleServiceNetworkingConnection(this, "vpc-connection-" + environment + "-" + user, GoogleServiceNetworkingConnectionConfig.builder()
                .network(vpc.getId())
                .service("servicenetworking.googleapis.com")
                .reservedPeeringRanges(List.of(privateIp.getName()))
                .build()
        );

        Storage storage = new Storage(this, "cloud-sql-" + environment + "-" + user,
                environment,
                user,
                project,
                privateVpcConnection,
                vpc.getId()
        );

        CloudFunction cloudFunction = new CloudFunction(this, "cloud-function-" + environment + "-" + user,
                environment,
                user,
                project,
                vpc.getId(),
                storage.getDbHost(),
                storage.getDbName(),
                storage.getDbUserName(),
                storage.getDbUserPassword()
        );

        this.httpsTriggerUrl = cloudFunction.getHttpsTriggerUrl();
    }

    public String getHttpsTriggerUrl(){
        return this.httpsTriggerUrl;
    }
}
```

Additionally, we provision a VPC for our Cloud SQL instance to reside.

### Storage

In the Storage class we create our Cloud SQL instance and DB user credentials for accessing the Cloud SQL instance. All attributes are made accessible as we will later use them in the creation of our Cloud Function

```java
public class Storage extends Resource {

    private String dbHost;
    private String dbName;
    private String dbUserName;
    private String dbUserPassword;

    public Storage(Construct scope, String id, String environment, String user, String project, GoogleServiceNetworkingConnection privateVpcConnection, String vpcId) {
        super(scope, id);

        GoogleSqlDatabaseInstance dbInstance = new GoogleSqlDatabaseInstance(this, "db-react-application-instance" + environment + "-" + user, GoogleSqlDatabaseInstanceConfig.builder()
                .name("db-react-application-instance" + environment + "-" + user)
                .project(project)
                .region("us-east1")
                .dependsOn(List.of(privateVpcConnection))
                .settings(GoogleSqlDatabaseInstanceSettings.builder()
                        .tier("db-f1-micro")
                        .availabilityType("REGIONAL")
                        .userLabels(new HashMap<>() {{
                            put("environment", environment);
                        }})
                        .ipConfiguration(GoogleSqlDatabaseInstanceSettingsIpConfiguration.builder()
                                .ipv4Enabled(false)
                                .privateNetwork(vpcId)
                                .build()
                        )
                        .build()
                )
                .databaseVersion("POSTGRES_13")
                .deletionProtection(false)
                .build()
        );

        GoogleSqlDatabase db = new GoogleSqlDatabase(this, "db-react-application-" + environment + "-" + user, GoogleSqlDatabaseConfig.builder()
                .name("db-react-application-" + environment + "-" + user)
                .project(project)
                .instance(dbInstance.getId())
                .build()
        );

        DataGoogleSecretManagerSecretVersion dbPass = new DataGoogleSecretManagerSecretVersion(this, "db_pass"+ environment + "-" + user, DataGoogleSecretManagerSecretVersionConfig.builder()
                .project(project)
                .secret(System.getenv("DB_PASS"))
                .build()
        );

        GoogleSqlUser dbUser = new GoogleSqlUser(this, "react-application-db-user-" + environment + "-" + user, GoogleSqlUserConfig.builder()
                .name("react-application-db-user-" + environment + "-" + user)
                .project(project)
                .instance(dbInstance.getId())
                .password(dbPass.getSecretData())
                .build()
        );

        this.dbHost = dbInstance.getPrivateIpAddress()+":5432";
        this.dbName = db.getName();
        this.dbUserName = dbUser.getName();
        this.dbUserPassword = dbUser.getPassword();

    }
}
```

### CloudFunction

```java
public class CloudFunction extends Resource {

    private String httpsTriggerUrl;

    public CloudFunction(Construct scope, String id, String environment, String user, String project, String vpcId, String dbHost, String dbName, String dbUserName, String dbPassword){
        super(scope, id);

        GoogleStorageBucket cloudFunctionStorage = new GoogleStorageBucket(this, "cloud-functions-" + environment + "-" + user, GoogleStorageBucketConfig.builder()
                //...
        );

        GoogleVpcAccessConnector vpcAccessConnector = new GoogleVpcAccessConnector(this, "msvmxw-tzag9-a9k2jl45f3s", GoogleVpcAccessConnectorConfig.builder()
                //...
        );

        ZipUtil.pack(new File(Paths.get(System.getProperty("user.dir"), "cloudfunctions", "api").toString()), new File(Paths.get(System.getProperty("user.dir"), "func_archive.zip").toString()));

        GoogleStorageBucketObject funcArchive = new GoogleStorageBucketObject(this, "functions-archive-" + environment + "-" + user, GoogleStorageBucketObjectConfig.builder()
                //...
        );

        GoogleCloudfunctionsFunction api = new GoogleCloudfunctionsFunction(this, "cloud-function-api-" + environment + "-" + user, GoogleCloudfunctionsFunctionConfig.builder()
                //...
        );

        new GoogleCloudfunctionsFunctionIamMember(this, "cloud-function-iam-" + environment + "-" + user, GoogleCloudfunctionsFunctionIamMemberConfig.builder()
                //...
        );

        this.httpsTriggerUrl = api.getHttpsTriggerUrl();
    }

    public String getHttpsTriggerUrl(){
        return this.httpsTriggerUrl;
    }
}
```

In our CloudFunction Class we first provision a Cloud Storage bucket to house the contents of the Cloud Function to be deployed.

```java
GoogleStorageBucket cloudFunctionStorage = new GoogleStorageBucket(this, "cloud-functions-" + environment + "-" + user, GoogleStorageBucketConfig.builder()
    .name("cloud-functions-" + environment + "-" + user)
    .project(project)
    .forceDestroy(true)
    .location("us-east1")
    .storageClass("STANDARD")
    .build()
);
```

We then zip the folder that contains our Cloud Function's implementation and create a Storage Bucket Object for the now zipped implementation

```java
ZipUtil.pack(new File(Paths.get(System.getProperty("user.dir"), "cloudfunctions", "api").toString()), new File(Paths.get(System.getProperty("user.dir"), "func_archive.zip").toString()));
GoogleStorageBucketObject funcArchive = new GoogleStorageBucketObject(this, "functions-archive-" + environment + "-" + user, GoogleStorageBucketObjectConfig.builder()
        .name("functions-archive-" + environment + "-" + user)
        .bucket(cloudFunctionStorage.getName())
        .source(Paths.get(System.getProperty("user.dir"), "func_archive.zip").toString())
        .build()
);
```

The VPC connector that will handle traffic between our Cloud Function and Cloud SQL DB

```java
GoogleVpcAccessConnector vpcAccessConnector = new GoogleVpcAccessConnector(this, "msvmxw-tzag9-a9k2jl45f3s", GoogleVpcAccessConnectorConfig.builder()
        .name("msvmxw-tzag9-a9k2jl45f3s")
        .project(project)
        .region("us-east1")
        .ipCidrRange("10.8.0.0/28")
        .network(vpcId)
        .build()
);
```

We finally create the Cloud Function and associative IAM role

```java
GoogleCloudfunctionsFunction api = new GoogleCloudfunctionsFunction(this, "cloud-function-api-" + environment + "-" + user, GoogleCloudfunctionsFunctionConfig.builder()
        .name("cloud-function-api-" + environment + "-" + user)
        .project(project)
        .region("us-east1")
        .runtime("nodejs14")
        .availableMemoryMb(128)
        .sourceArchiveBucket(cloudFunctionStorage.getName())
        .sourceArchiveObject(funcArchive.getName())
        .triggerHttp(true)
        .entryPoint("app")
        .environmentVariables(new HashMap<>(){{
            put("DB_HOST", dbHost);
            put("DB_USER", dbUserName);
            put("DB_PASS", dbPassword);
            put("DB_NAME", dbName);
        }})
        .vpcConnector(vpcAccessConnector.getId())
        .build()
);

new GoogleCloudfunctionsFunctionIamMember(this, "cloud-function-iam-" + environment + "-" + user, GoogleCloudfunctionsFunctionIamMemberConfig.builder()
        .cloudFunction(api.getName())
        .project(project)
        .region("us-east1")
        .role("roles/cloudfunctions.invoker")
        .member("allUsers")
        .build()
);
```

The trigger url for our Cloud Function is made accessible so we later hand it off to the Frontend of our react app

```java
this.httpsTriggerUrl = api.getHttpsTriggerUrl();
```

## Frontend

We will host the contents of our website statically in a Google Storage Bucket– default permissions for accessing objects in this bucket are then given

```java
GoogleStorageBucket bucket = new GoogleStorageBucket(this, "cdktfpython-static-site-" + environment + "-" + user, GoogleStorageBucketConfig.builder()
        .name("cdktfpython-static-site-" + environment + "-" + user)
        .project(project)
        .location("us-east1")
        .storageClass("STANDARD")
        .forceDestroy(true)
        .website(GoogleStorageBucketWebsite.builder()
        .mainPageSuffix("index.html")
        .notFoundPage("index.html")
        .build()
    )
    .build()
);

new GoogleStorageDefaultObjectAccessControl(this, "bucket-access-control-" + environment + "-" + user, GoogleStorageDefaultObjectAccessControlConfig.builder()
    .bucket(bucket.getName())
    .role("READER")
    .entity("allUsers")
    .build()
);
```

Here we reserve a static external IP address– we will later attach it our URL Maps.

```java
GoogleComputeGlobalAddress externalIP = new GoogleComputeGlobalAddress(this, "external-react-app-ip-" + environment + "-" + user, GoogleComputeGlobalAddressConfig.builder()
        .name("external-react-app-ip-" + environment + "-" + user)
        .project(project)
        .addressType("EXTERNAL")
        .ipVersion("IPV4")
        .description("IP address for React app")
        .build()
);
```

A GoogleComputeBackendBucket is used to access the static site files with HTTPS load balancing

```java
GoogleComputeBackendBucket staticSite = new GoogleComputeBackendBucket(this, "static-site-backend" + environment + "-" + user, GoogleComputeBackendBucketConfig.builder()
        .name("static-site-backend" + environment + "-" + user)
        .project(project)
        .description("Contains files needed by the website")
        .bucketName(bucket.getName())
        .enableCdn(true)
        .build()
);
```

We define URL Maps for both HTTPS and HTTP targets so as to use HTTPS redirect in our applications load balancer. Additionally we create a SSL certificate and attach it to our HTTPS target.

```python
GoogleComputeManagedSslCertificate sslCertificate = new GoogleComputeManagedSslCertificate(this, "ssl-certificate-" + environment + "-" + user, GoogleComputeManagedSslCertificateConfig.builder()
        .name("ssl-certificate-" + environment + "-" + user)
        .project(project)
        .managed(GoogleComputeManagedSslCertificateManaged.builder()
                .domains(List.of("cdktfpython.com", "www.cdktfpython.com"))
                .build()
        )
        .build()
);

GoogleComputeUrlMap webHttps = new GoogleComputeUrlMap(this, "web-url-map-https-" + environment + "-" + user, GoogleComputeUrlMapConfig.builder()
        .name("web-url-map-https" + environment + "-" + user)
        .project(project)
        .defaultService(staticSite.getSelfLink())
        .build()
);

GoogleComputeTargetHttpsProxy httpsProxy = new GoogleComputeTargetHttpsProxy(this, "web-target-proxy-https-" + environment + "-" + user, GoogleComputeTargetHttpsProxyConfig.builder()
        .name("web-target-proxy-https-" + environment + "-" + user)
        .project(project)
        .urlMap(webHttps.getId())
        .sslCertificates(List.of(sslCertificate.getSelfLink()))
        .build()
);

new GoogleComputeGlobalForwardingRule(this, "web-forwarding-rule-https-" + environment + "-" + user, GoogleComputeGlobalForwardingRuleConfig.builder()
        .name("web-forwarding-rule-https-" + environment + "-" + user)
        .project(project)
        .loadBalancingScheme("EXTERNAL")
        .ipAddress(externalIP.getAddress())
        .ipProtocol("TCP")
        .portRange("443")
        .target(httpsProxy.getSelfLink())
        .build()
);

GoogleComputeUrlMap webHttp = new GoogleComputeUrlMap(this,"web-url-map-http-" + environment + "-" + user, GoogleComputeUrlMapConfig.builder()
        .name("web-url-map-http-" + environment + "-" + user)
        .project(project)
        .description("Web HTTP load balancer")
        .defaultUrlRedirect(GoogleComputeUrlMapDefaultUrlRedirect.builder()
                .httpsRedirect(true)
                .stripQuery(true)
                .build()
        )
        .build()
);

GoogleComputeTargetHttpProxy httpProxy = new GoogleComputeTargetHttpProxy(this , "web-target-proxy-http-" + environment + "-" + user, GoogleComputeTargetHttpProxyConfig.builder()
        .name("web-target-proxy-http-" + environment + "-" + user)
        .project(project)
        .description("HTTP target proxy")
        .urlMap(webHttp.getSelfLink())
        .build()
);

new GoogleComputeGlobalForwardingRule(this, "web-forwarding-rule-http-" + environment + "-" + user, GoogleComputeGlobalForwardingRuleConfig.builder()
        .name("web-forwarding-rule-http-" + environment + "-" + user)
        .project(project)
        .loadBalancingScheme("EXTERNAL")
        .ipAddress(externalIP.getAddress())
        .ipProtocol("TCP")
        .target(httpProxy.getId())
        .portRange("80")
        .build()
);
```

Lastly, we create environment variables for our GoogleStorageBucket's name (for uploading the static site file) and our HTTPS trigger URL (for making requests to Cloud Function) to our Frontend implementation.

```python
new File(this, "env", FileConfig.builder()
        .filename(Paths.get(System.getProperty("user.dir"), "frontend","code", ".env.production.local").toString())
        .content("BUCKET_NAME="+bucket.getName()+"\nREACT_APP_API_ENDPOINT="+httpsTriggerUrl)
        .build()
);
```

## License

[Mozilla Public License v2.0](https://github.com/hashicorp/cdktf-integration-serverless-java-gcp-example/blob/main/LICENSE)
