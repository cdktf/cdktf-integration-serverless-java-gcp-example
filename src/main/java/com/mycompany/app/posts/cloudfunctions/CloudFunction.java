package com.mycompany.app.posts.cloudfunctions;

import imports.google_beta.google_storage_bucket.*;
import imports.google_beta.google_vpc_access_connector.*;
import imports.google_beta.google_storage_bucket_object.*;
import imports.google_beta.google_cloudfunctions_function.*;
import imports.google_beta.google_cloudfunctions_function_iam_member.*;
import org.zeroturnaround.zip.ZipUtil;
import com.hashicorp.cdktf.Resource;
import software.constructs.Construct;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;

public class CloudFunction extends Resource {

    private String httpsTriggerUrl;

    public CloudFunction(Construct scope, String id, String environment, String user, String project, String vpcId, String dbHost, String dbName, String dbUserName, String dbPassword){
        super(scope, id);

        GoogleStorageBucket cloudFunctionStorage = new GoogleStorageBucket(this, "cloud-functions-" + environment + "-" + user, GoogleStorageBucketConfig.builder()
                .name("cloud-functions-" + environment + "-" + user)
                .project(project)
                .forceDestroy(true)
                .location("us-east1")
                .storageClass("STANDARD")
                .build()
        );

        GoogleVpcAccessConnector vpcAccessConnector = new GoogleVpcAccessConnector(this, "msvmxw-tzag9-a9k2jl45f3s", GoogleVpcAccessConnectorConfig.builder()
                .name("msvmxw-tzag9-a9k2jl45f3s")
                .project(project)
                .region("us-east1")
                .ipCidrRange("10.8.0.0/28")
                .network(vpcId)
                .build()
        );

        ZipUtil.pack(new File(Paths.get(System.getProperty("user.dir"), "cloudfunctions", "api").toString()), new File(Paths.get(System.getProperty("user.dir"), "func_archive.zip").toString()));

        GoogleStorageBucketObject funcArchive = new GoogleStorageBucketObject(this, "functions-archive-" + environment + "-" + user, GoogleStorageBucketObjectConfig.builder()
                .name("functions-archive-" + environment + "-" + user)
                .bucket(cloudFunctionStorage.getName())
                .source(Paths.get(System.getProperty("user.dir"), "func_archive.zip").toString())
                .build()
        );

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

        this.httpsTriggerUrl = api.getHttpsTriggerUrl();
    }

    public String getHttpsTriggerUrl(){
        return this.httpsTriggerUrl;
    }


}
