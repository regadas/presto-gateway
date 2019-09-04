package com.lyft.data.gateway.ha.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.lyft.data.gateway.config.ProxyBackendConfiguration;
import com.lyft.data.gateway.ha.persistence.JdbcConnectionManager;
import com.lyft.data.gateway.ha.persistence.dao.GatewayBackend;
import io.dropwizard.views.View;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path("admin")
public class AdminResource {

  @Inject private JdbcConnectionManager connectionManager;
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @GET
  @Produces(MediaType.TEXT_HTML)
  public AdminView adminUi() {
    return new AdminView("/template/admin-view.ftl");
  }

  public static class AdminView extends View {
    protected AdminView(String templateName) {
      super(templateName, Charset.defaultCharset());
    }
  }

  private enum EntityType {
    GATEWAY_BACKEND
  }

  @GET
  @Path("/entity")
  @Produces(MediaType.APPLICATION_JSON)
  public List<EntityType> getAllEntityTypes() {
    return Arrays.asList(EntityType.values());
  }

  @GET
  @Path("/entity/{entityType}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getAllEntitiesForType(@PathParam("entityType") String entityTypeStr) {
    EntityType entityType = EntityType.valueOf(entityTypeStr);

    switch (entityType) {
      case GATEWAY_BACKEND:
        List<ProxyBackendConfiguration> backends;
        try {
          connectionManager.open();
          backends = GatewayBackend.upcast(GatewayBackend.findAll());
        } finally {
          connectionManager.close();
        }
        return Response.ok(backends).build();
      default:
    }
    return Response.ok(ImmutableList.of()).build();
  }

  @POST
  @Path("/entity")
  public Response updateEntity(@QueryParam("entityType") String entityTypeStr, String jsonPayload) {
    if (Strings.isNullOrEmpty(entityTypeStr)) {
      throw new WebApplicationException("EntryType can not be null");
    }

    EntityType entityType = EntityType.valueOf(entityTypeStr);
    try {
      switch (entityType) {
        case GATEWAY_BACKEND:
          try {
            connectionManager.open();
            ProxyBackendConfiguration backend =
                OBJECT_MAPPER.readValue(jsonPayload, ProxyBackendConfiguration.class);
            boolean create = true;
            if (GatewayBackend.findById(backend.getName()) != null) {
              create = false;
            }
            if (create) {
              GatewayBackend.create(new GatewayBackend(), backend);
            } else {
              GatewayBackend.update(new GatewayBackend(), backend);
            }
          } finally {
            connectionManager.close();
          }
          break;
        default:
      }
    } catch (IOException e) {
      log.error(e.getMessage(), e);
      throw new WebApplicationException(e);
    }
    return Response.ok().build();
  }
}
