package com.mycompany.app;

import imports.google_beta.google_compute_project_default_network_tier.GoogleComputeProjectDefaultNetworkTier;
import imports.google_beta.google_compute_project_default_network_tier.GoogleComputeProjectDefaultNetworkTierConfig;
import com.mycompany.app.frontend.Frontend;
import com.mycompany.app.posts.*;
import com.hashicorp.cdktf.*;
import software.constructs.Construct;

import imports.google_beta.google_beta_provider.GoogleBetaProvider;
import imports.google_beta.google_beta_provider.GoogleBetaProviderConfig;
import imports.local.local_provider.LocalProvider;

public class Main {

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

    public static class PostsStack extends TerraformStack{

        private String httpsTriggerUrl;

        public PostsStack(Construct scope, String name, String environment, String user, String project){
            super(scope, name);

            new GoogleBetaProvider(this, "google-beta", GoogleBetaProviderConfig.builder()
                    .region("us-east1")
                    .project(project)
                    .build()
            );

            TerraformVariable dbPass = new TerraformVariable(this, "DB_PASS", TerraformVariableConfig.builder()
                    .type("string")
                    .sensitive(true)
                    .description("The password for the database")
                    .build()
            );

            Posts posts = new Posts(this, "posts-" + environment + "-" + user, environment, user, project, dbPass.getStringValue());

            this.httpsTriggerUrl = posts.getHttpsTriggerUrl();
        }

        public String getHttpsTriggerUrl(){
            return this.httpsTriggerUrl;
        }
    }

    public static void main(String[] args) {
        final App app = new App();

        Boolean USE_REMOTE_BACKEND = (System.getenv("USE_REMOTE_BACKEND") == "true");

        //DEV
        PostsStack postsDev = new PostsStack(app, "posts-dev", "development", System.getenv("CDKTF_USER"), System.getenv("PROJECT_ID"));
        if (USE_REMOTE_BACKEND) {
            new RemoteBackend(postsDev, RemoteBackendProps.builder()
                    .organization("terraform-demo-mad")
                    .workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"))
                    .build()
            );
        }
        FrontendStack frontendDev = new FrontendStack(app, "frontend-dev", "development", System.getenv("CDKTF_USER"), System.getenv("PROJECT_ID"), postsDev.getHttpsTriggerUrl());
        if (USE_REMOTE_BACKEND) {
            new RemoteBackend(frontendDev, RemoteBackendProps.builder()
                    .organization("terraform-demo-mad")
                    .workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"))
                    .build()
            );
        }

        //Prod
        PostsStack postsProd = new PostsStack(app, "posts-prod", "production", System.getenv("CDKTF_USER"), System.getenv("PROJECT_ID"));
        if (USE_REMOTE_BACKEND) {
            new RemoteBackend(postsProd, RemoteBackendProps.builder()
                    .organization("terraform-demo-mad")
                    .workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"))
                    .build()
            );
        }
        FrontendStack frontendProd = new FrontendStack(app, "frontend-prod", "production", System.getenv("CDKTF_USER"), System.getenv("PROJECT_ID"), postsProd.getHttpsTriggerUrl());
        if (USE_REMOTE_BACKEND) {
            new RemoteBackend(frontendProd, RemoteBackendProps.builder()
                    .organization("terraform-demo-mad")
                    .workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"))
                    .build()
            );
        }

        app.synth();
    }
}