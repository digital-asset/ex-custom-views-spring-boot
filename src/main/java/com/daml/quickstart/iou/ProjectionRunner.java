package com.daml.quickstart.iou;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import com.daml.ledger.javaapi.data.CreatedEvent;
import com.daml.ledger.javaapi.data.Event;
import com.daml.lf.codegen.json.JsonCodec;
import com.daml.projection.Bind;
import com.daml.projection.ExecuteUpdate;
import com.daml.projection.JdbcAction;
import com.daml.projection.JdbcProjector;
import com.daml.projection.Project;
import com.daml.projection.Projection;
import com.daml.projection.ProjectionFilter;
import com.daml.projection.ProjectionId;
import com.daml.projection.ProjectionTable;
import com.daml.projection.javadsl.BatchSource;
import com.daml.projection.javadsl.Control;
import com.daml.projection.javadsl.Projector;
import com.daml.quickstart.model.iou.Iou;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

public class ProjectionRunner {
  private static final Logger logger = LoggerFactory.getLogger(ProjectionRunner.class);

  public static void main(String[] args) {
    if(args.length < 1)
      throw new IllegalArgumentException("An argument for party of Alice is expected.");

    var aliceParty = args[0];

    // Setup db params
    String url = "jdbc:postgresql://localhost/ious";
    String user = "postgres";
    String password = "postgres";

    // create actor system used by projector and grpc client
    ActorSystem system = ActorSystem.create("iou-projection");

    // setup datasource and projection table
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(url);
    config.setUsername(user);
    config.setPassword(password);
    DataSource dataSource = new HikariDataSource(config);
    ProjectionTable projectionTable = new ProjectionTable("events");

    // create projector
    Projector<JdbcAction> projector = JdbcProjector.create(dataSource, system);

    // Create a projection
    Projection<Event> events =
        Projection.create(new ProjectionId("active-iou-contracts-for-alice"), ProjectionFilter.parties(Set.of(aliceParty)));

    var jsonCodec = JsonCodec.encodeAsNumbers();

    Project<Event, JdbcAction> f = envelope -> {
      Event event = envelope.getEvent();
      logger.info("projecting event " + event.getEventId());
      if (event instanceof CreatedEvent) {
        Iou.Contract iou = Iou.Contract.fromCreatedEvent((CreatedEvent) event);
        var action =
            ExecuteUpdate.create(
                    "insert into "
                        + projectionTable.getName()
                        + "(contract_id, event_id, amount, currency, json_data) "
                        + "values (?, ?, ?, ?, ?::jsonb)")
                .bind(1, event.getContractId(), Bind.String())
                .bind(2, event.getEventId(), Bind.String())
                .bind(3, iou.data.amount, Bind.BigDecimal())
                .bind(4, iou.data.currency, Bind.String())
                .bind(5, jsonCodec.toJsValue(iou.data.toValue()).compactPrint(), Bind.String());
        return List.of(action);
      }
      else {
        var action =
            ExecuteUpdate.create(
                    "delete from " +
                        projectionTable.getName() +
                        " where contract_id = ?"
                )
                .bind(1, event.getContractId(), Bind.String());
        return List.of(action);
      }
    };

    GrpcClientSettings grpcClientSettings = GrpcClientSettings
        .connectToServiceAt("localhost", 6865, system)
        .withTls(false);
    var source = BatchSource.events(grpcClientSettings);

    logger.info("Starting projection");

    Control control = projector.project(source, events, f);

    control.failed().whenComplete((throwable, ignored) -> {
      if (throwable instanceof NoSuchElementException)
        logger.info("Projection finished.");
      else
        logger.error("Failed to run Projection.", throwable);
      control.resourcesClosed().thenRun(system::terminate);
    });
  }
}
