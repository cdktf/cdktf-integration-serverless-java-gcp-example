package com.mycompany.app.frontend;

import com.hashicorp.cdktf.providers.local.file.File;
import com.hashicorp.cdktf.providers.local.file.FileConfig;
import com.hashicorp.cdktf.providers.google_beta.google_storage_bucket.GoogleStorageBucket;
import com.hashicorp.cdktf.providers.google_beta.google_storage_bucket.GoogleStorageBucketConfig;
import com.hashicorp.cdktf.providers.google_beta.google_storage_bucket.GoogleStorageBucketWebsite;
import com.hashicorp.cdktf.providers.google_beta.google_storage_default_object_access_control.GoogleStorageDefaultObjectAccessControl;
import com.hashicorp.cdktf.providers.google_beta.google_storage_default_object_access_control.GoogleStorageDefaultObjectAccessControlConfig;
import com.hashicorp.cdktf.providers.google_beta.google_compute_backend_bucket.GoogleComputeBackendBucket;
import com.hashicorp.cdktf.providers.google_beta.google_compute_backend_bucket.GoogleComputeBackendBucketConfig;
import com.hashicorp.cdktf.providers.google_beta.google_compute_managed_ssl_certificate.GoogleComputeManagedSslCertificate;
import com.hashicorp.cdktf.providers.google_beta.google_compute_managed_ssl_certificate.GoogleComputeManagedSslCertificateConfig;
import com.hashicorp.cdktf.providers.google_beta.google_compute_managed_ssl_certificate.GoogleComputeManagedSslCertificateManaged;
import com.hashicorp.cdktf.providers.google_beta.google_compute_target_https_proxy.GoogleComputeTargetHttpsProxy;
import com.hashicorp.cdktf.providers.google_beta.google_compute_target_https_proxy.GoogleComputeTargetHttpsProxyConfig;
import com.hashicorp.cdktf.providers.google_beta.google_compute_target_http_proxy.GoogleComputeTargetHttpProxy;
import com.hashicorp.cdktf.providers.google_beta.google_compute_target_http_proxy.GoogleComputeTargetHttpProxyConfig;
import com.hashicorp.cdktf.providers.google_beta.google_compute_url_map.GoogleComputeUrlMap;
import com.hashicorp.cdktf.providers.google_beta.google_compute_url_map.GoogleComputeUrlMapConfig;
import com.hashicorp.cdktf.providers.google_beta.google_compute_url_map.GoogleComputeUrlMapDefaultUrlRedirect;
import com.hashicorp.cdktf.providers.google_beta.google_compute_global_forwarding_rule.GoogleComputeGlobalForwardingRule;
import com.hashicorp.cdktf.providers.google_beta.google_compute_global_forwarding_rule.GoogleComputeGlobalForwardingRuleConfig;
import com.hashicorp.cdktf.providers.google_beta.google_compute_global_address.GoogleComputeGlobalAddress;
import com.hashicorp.cdktf.providers.google_beta.google_compute_global_address.GoogleComputeGlobalAddressConfig;
import software.constructs.Construct;

import java.nio.file.Paths;
import java.util.List;

public class Frontend extends Construct {

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
