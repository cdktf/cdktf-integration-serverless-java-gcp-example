package com.mycompany.app.posts;

import com.mycompany.app.posts.cloudfunctions.CloudFunction;
import com.mycompany.app.posts.Storage;
import com.hashicorp.cdktf.Resource;
import imports.google_beta.google_compute_global_address.GoogleComputeGlobalAddress;
import imports.google_beta.google_compute_global_address.GoogleComputeGlobalAddressConfig;
import imports.google_beta.google_compute_network.GoogleComputeNetwork;
import imports.google_beta.google_compute_network.GoogleComputeNetworkConfig;
import imports.google_beta.google_service_networking_connection.GoogleServiceNetworkingConnection;
import imports.google_beta.google_service_networking_connection.GoogleServiceNetworkingConnectionConfig;
import software.constructs.Construct;

import java.util.List;


public class Posts extends Resource {

    private String httpsTriggerUrl;

    public Posts(Construct scope, String id, String environment, String user, String project, String dbPass){
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
                vpc.getId(),
                dbPass
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
