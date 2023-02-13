package org.trustmd.resources;

import lombok.SneakyThrows;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.hyperledger.fabric.gateway.*;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.security.CryptoSuiteFactory;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.trustmd.model.Trust;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;


@javax.ws.rs.Path("/trustmd")
@ApplicationScoped
@OpenAPIDefinition(info = @Info(title = "trustmd endpoint", version = "1.0"))
public class TrustMd {
    private static final Log _logger = LogFactory.getLog(TrustMd.class);
    private final static Path CURRENT_HOME_PATH = Paths.get(System.getProperty("user.home"));
    private final static Path ROOT_PATH = Paths.get(CURRENT_HOME_PATH.toString(), "workspace", "minifab", "trustmdtest");
    private final static Path CONNECTIONS_PATH = Paths.get(ROOT_PATH.toString(), "Connections");
    private final static String CONNECTIONS_FILE = "/v1-3p-connection.json";

    private static final String ORGNAME_ORG1 = "org1.example.com";
    private static final String ADMINNAME_ORG1 = "admin";
    private static final String ADMINPWD_ORG1 = "adminpw";
    private static final String CA_CERT_ORG1 = "profiles/" + ORGNAME_ORG1 + "/tls/" + "ca.org1.example.com-cert.pem";
    private static final String MSPID_ORG1 = "org1-example-com";


    static {
        System.setProperty("org.hyperledger.fabric.sdk.service_discovery.as_localhost", "true");
    }

    @Timed(name = "createTrustTime", tags = {
            "method=post" }, absolute = true, description = "Time needed to add a trust value to the ledger")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @javax.ws.rs.Path("/createTrust")
    @Operation(summary = "Add a new Trust info to the ledger", description = "Requires a unique key to be successfully executed")
    public Response createTrust(Trust aTrust) {
        try {
            _logger.info("Getting wallet");
            final Path walletPath = Paths.get(ROOT_PATH.toString(), "vars", "app", "go", "wallets");
            final Wallet wallet = Wallets.newFileSystemWallet(walletPath);

            // Loading connections config file from expected path
            final Path networkConfigPath = Paths.get(CONNECTIONS_PATH + CONNECTIONS_FILE);
            _logger.info("Network config path: " + networkConfigPath);

            System.out.println("createTrust with: " + aTrust.toJSONString());
            // expecting wallet directory within the default server location
            // wallet exported from Fabric wallets

            final Gateway.Builder gBuilder = Gateway.createBuilder()
                    .identity(wallet, "Admin")
                    .networkConfig(networkConfigPath)
                    .discovery(false);

            try (final Gateway gateway = gBuilder.connect()) {
                final Network network = gateway.getNetwork("mychannel");
                final Contract contract = network.getContract("trustmd");
                final byte[] result = contract.submitTransaction("createTrust",
                        aTrust.getClusterHeadId(),
                        aTrust.getEvaluatedNodeId(),
                        aTrust.getTrustDecision(),
                        aTrust.getDegreeOfBelief(),
                        aTrust.getMecHostId());
                return Response.status(Response.Status.OK).entity(Arrays.toString(result)).build();
            } catch (Exception exception) {
                throw new javax.ws.rs.ServerErrorException("Unable to get network/contract and execute query", Response.Status.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception exception) {
            // Initially raise ServiceUnavailable
            throw new javax.ws.rs.ServerErrorException("Unable to find config or wallet", Response.Status.NOT_FOUND);
        }
    }
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @javax.ws.rs.Path("/walletTest")
    public Response get(Trust aTrust) throws IOException {
        final Path walletPath = Paths.get(ROOT_PATH.toString(), "vars", "app", "go", "wallets");
        final Wallet wallet = Wallets.newFileSystemWallet(walletPath);

        final Identity adminIdentity = wallet.get("Admin");
        if (adminIdentity != null) {
            System.out.println("Entity adminIdentity already registered");
        }

        // Loading connections config file from expected path
        final Path networkConfigPath = Paths.get(CONNECTIONS_PATH + CONNECTIONS_FILE);
        System.out.println("Network config path: " + networkConfigPath);

        // expecting wallet directory within the default server location
        // wallet exported from Fabric wallets
        try {
            final Gateway.Builder gBuilder = Gateway.createBuilder()
                    .identity(wallet, "Admin")
                    .networkConfig(networkConfigPath)
                    .discovery(true);
            try (final Gateway gateway = gBuilder.connect()) {
                final Network network = gateway.getNetwork("mychannel");
                final Contract contract = network.getContract("trustmd");
                System.out.println(contract);
            }

        } catch (Exception exception) {
            throw new javax.ws.rs.ServerErrorException("Unable to get network/contract and execute query", Response.Status.INTERNAL_SERVER_ERROR);
        }
        return Response.status(Response.Status.OK).entity("OK").build();
    }

}
