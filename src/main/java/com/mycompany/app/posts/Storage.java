package com.mycompany.app.posts;

import com.hashicorp.cdktf.Resource;
import imports.google_beta.google_service_networking_connection.GoogleServiceNetworkingConnection;
import imports.google_beta.google_sql_database.GoogleSqlDatabase;
import imports.google_beta.google_sql_database.GoogleSqlDatabaseConfig;
import imports.google_beta.google_sql_database_instance.GoogleSqlDatabaseInstance;
import imports.google_beta.google_sql_database_instance.GoogleSqlDatabaseInstanceConfig;
import imports.google_beta.google_sql_user.GoogleSqlUser;
import imports.google_beta.google_sql_user.GoogleSqlUserConfig;
import imports.google_beta.google_sql_database_instance.GoogleSqlDatabaseInstanceSettings;
import imports.google_beta.google_sql_database_instance.GoogleSqlDatabaseInstanceSettingsIpConfiguration;
import imports.google_beta.data_google_secret_manager_secret_version.DataGoogleSecretManagerSecretVersion;
import imports.google_beta.data_google_secret_manager_secret_version.DataGoogleSecretManagerSecretVersionConfig;

import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;


public class Storage extends Resource {

    private String dbHost;
    private String dbName;
    private String dbUserName;
    private String dbUserPassword;

    public Storage(Construct scope, String id, String environment, String user, String project, GoogleServiceNetworkingConnection privateVpcConnection, String vpcId, String dbPassword){
        super(scope, id);

        GoogleSqlDatabaseInstance dbInstance = new GoogleSqlDatabaseInstance(this, "db-react-application-instance" + environment + "-" + user, GoogleSqlDatabaseInstanceConfig.builder()
                .name("db-react-application-instance" + environment + "-" + user)
                .project(project)
                .region("us-east1")
                .dependsOn(List.of(privateVpcConnection))
                .settings(GoogleSqlDatabaseInstanceSettings.builder()
                        .tier("db-f1-micro")
                        .availabilityType("REGIONAL")
                        .userLabels(new HashMap<>(){{
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
                .secret(dbPassword)
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
        this.dbUserPassword = dbPass.getSecretData();

    }

    public String getDbHost(){
        return this.dbHost;
    }

    public String getDbName(){
        return this.dbName;
    }

    public String getDbUserName(){
        return this.dbUserName;
    }

    public String getDbUserPassword(){
        return this.dbUserPassword;
    }

}
