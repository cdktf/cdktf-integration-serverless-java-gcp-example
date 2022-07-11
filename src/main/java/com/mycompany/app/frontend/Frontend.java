package com.mycompany.app.frontend;

import com.hashicorp.cdktf.Resource;
import com.hashicorp.cdktf.providers.local.File;
import com.hashicorp.cdktf.providers.local.FileConfig;
import com.hashicorp.cdktf.providers.google_beta.GoogleStorageBucket;
import com.hashicorp.cdktf.providers.google_beta.GoogleStorageBucketConfig;
import com.hashicorp.cdktf.providers.google_beta.GoogleStorageBucketWebsite;
import com.hashicorp.cdktf.providers.google_beta.GoogleStorageDefaultObjectAccessControl;
import com.hashicorp.cdktf.providers.google_beta.GoogleStorageDefaultObjectAccessControlConfig;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeBackendBucket;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeBackendBucketConfig;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeManagedSslCertificate;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeManagedSslCertificateConfig;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeManagedSslCertificateManaged;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeTargetHttpsProxy;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeTargetHttpsProxyConfig;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeTargetHttpProxy;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeTargetHttpProxyConfig;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeUrlMap;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeUrlMapConfig;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeGlobalForwardingRule;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeGlobalForwardingRuleConfig;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeUrlMapDefaultUrlRedirect;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeGlobalAddress;
import com.hashicorp.cdktf.providers.google_beta.GoogleComputeGlobalAddressConfig;
import software.constructs.Construct;

import java.nio.file.Paths;
import java.util.List;

public class Frontend extends Resource {

    public Frontend(Construct scope, String id, String project, String environment, String user, String httpsTriggerUrl){
        super(scope, id);

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

        GoogleComputeGlobalAddress externalIP = new GoogleComputeGlobalAddress(this, "external-react-app-ip-" + environment + "-" + user, GoogleComputeGlobalAddressConfig.builder()
                .name("external-react-app-ip-" + environment + "-" + user)
                .project(project)
                .addressType("EXTERNAL")
                .ipVersion("IPV4")
                .description("IP address for React app")
                .build()
        );

        GoogleComputeBackendBucket staticSite = new GoogleComputeBackendBucket(this, "static-site-backend" + environment + "-" + user, GoogleComputeBackendBucketConfig.builder()
                .name("static-site-backend" + environment + "-" + user)
                .project(project)
                .description("Contains files needed by the website")
                .bucketName(bucket.getName())
                .enableCdn(true)
                .build()
        );

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

        new File(this, "env", FileConfig.builder()
                .filename(Paths.get(System.getProperty("user.dir"), "frontend","code", ".env.production.local").toString())
                .content("BUCKET_NAME="+bucket.getName()+"\nREACT_APP_API_ENDPOINT="+httpsTriggerUrl)
                .build()
        );
    }
}
