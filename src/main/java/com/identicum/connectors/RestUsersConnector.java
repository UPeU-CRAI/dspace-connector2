package com.identicum.connectors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.*;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.operations.TestApiOp;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.*;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;

import org.json.JSONArray;
import org.json.JSONObject;

import com.evolveum.polygon.rest.AbstractRestConnector;
import com.identicum.connectors.RestUsersConfiguration;
import com.identicum.connectors.RestUsersFilter;
import com.identicum.connectors.RestUsersFilterTranslator;


// ==============================
// Bloque de Definición del Conector
// ==============================

@ConnectorClass(displayNameKey = "connector.identicum.rest.display", configurationClass = RestUsersConfiguration.class)
public class RestUsersConnector 
    extends AbstractRestConnector<RestUsersConfiguration>
    implements CreateOp, UpdateOp, SchemaOp, SearchOp<RestUsersFilter>, DeleteOp, UpdateAttributeValuesOp, TestOp, TestApiOp {

    private static final Log LOG = Log.getLog(RestUsersConnector.class);

    // ==============================
    // Bloque de Variables y Constantes
    // ==============================

    // Endpoints para usuarios y roles
    private static final String USERS_ENDPOINT = "/server/api/eperson/epersons";
    private static final String ROLES_ENDPOINT = "/server/api/eperson/groups";

    // ==============================
    // Bloque de Definición de Atributos
    // ==============================
    // Definición de los atributos que serán utilizados para la gestión de usuarios en DSpace.
    public static final String ATTR_ID = "uuid";
    public static final String ATTR_USERNAME = "username"; // Representa el "name" en la respuesta JSON, usado como identificador o nombre de usuario
    public static final String ATTR_EMAIL = "email";
    public static final String ATTR_FIRST_NAME = "eperson.firstname";
    public static final String ATTR_LAST_NAME = "eperson.lastname";
    public static final String ATTR_CAN_LOG_IN = "canLogIn";
    public static final String ATTR_LAST_ACTIVE = "lastActive";
    public static final String ATTR_REQUIRE_CERTIFICATE = "requireCertificate";
    public static final String ATTR_NET_ID = "netid";
    public static final String ATTR_SELF_REGISTERED = "selfRegistered";
    public static final String ATTR_ALERT_EMBARGO = "eperson.alert.embargo";
    public static final String ATTR_LANGUAGE = "eperson.language";
    public static final String ATTR_LICENSE_ACCEPTED = "eperson.license.accepted";
    public static final String ATTR_LICENSE_ACCEPTED_DATE = "eperson.license.accepteddate";
    public static final String ATTR_ORCID_SCOPE = "eperson.orcid.scope";
    public static final String ATTR_ORCID = "eperson.orcid";
    public static final String ATTR_PHONE = "eperson.phone";


    // ==============================
    // Bloque de authManager y Autenticación
    // ==============================

    // authManager para manejar la autenticación
    private AuthManager authManager;

    private void ensureAuthManagerInitialized() {
        if (authManager == null) {
            String password = getClearPassword(getConfiguration().getPassword());

            authManager = new AuthManager(
                    getConfiguration().getServiceAddress(),
                    getConfiguration().getUsername(),
                    password
            );
        }
    }

    private String getClearPassword(GuardedString guardedPassword) {
        final StringBuilder passwordBuilder = new StringBuilder();
        guardedPassword.access(clearChars -> passwordBuilder.append(new String(clearChars)));
        return passwordBuilder.toString();
    }

    // ==============================
    // Bloque de Operaciones CRUD
    // ==============================

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> attributes, OperationOptions options) {
      ensureAuthManagerInitialized();
        LOG.ok("Entering create with ObjectClass: {0}", objectClass.getObjectClassValue());

        if (!objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new UnsupportedOperationException("Create operation is not supported for object class: " + objectClass.getObjectClassValue());
        }

        // Construir el objeto JSON con los atributos
        JSONObject jsonObject = new JSONObject();
        for (Attribute attr : attributes) {
            String attrName = attr.getName();
            Object attrValue = attr.getValue().get(0); // Asumiendo que es single-valued
            jsonObject.put(attrName, attrValue);
            LOG.ok("Added attribute {0}: {1}", attrName, attrValue);
        }

        // Realizar la solicitud HTTP POST
        String endpoint = getConfiguration().getServiceAddress() + USERS_ENDPOINT;
        HttpPost request = new HttpPost(endpoint);
        JSONObject response = callRequest(request, jsonObject);

        // Obtener el UID del nuevo usuario
        String uidValue = response.getString("uuid");
        LOG.ok("Created user with UID: {0}", uidValue);
        return new Uid(uidValue);
    }

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
      ensureAuthManagerInitialized();
        LOG.ok("Entering update with ObjectClass: {0}, UID: {1}", objectClass.getObjectClassValue(), uid.getUidValue());

        if (!objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new UnsupportedOperationException("Update operation is not supported for object class: " + objectClass.getObjectClassValue());
        }

        // Construir el objeto JSON con los atributos a actualizar
        JSONObject jsonObject = new JSONObject();
        for (Attribute attr : replaceAttributes) {
            String attrName = attr.getName();
            Object attrValue = attr.getValue().get(0); // Asumiendo que es single-valued
            jsonObject.put(attrName, attrValue);
            LOG.ok("Updating attribute {0}: {1}", attrName, attrValue);
        }

        // Realizar la solicitud HTTP PUT
        String endpoint = getConfiguration().getServiceAddress() + USERS_ENDPOINT + "/" + uid.getUidValue();
        HttpPut request = new HttpPut(endpoint);
        JSONObject response = callRequest(request, jsonObject);

        // Retornar el UID actualizado
        String uidValue = response.getString("uuid");
        LOG.ok("Updated user with UID: {0}", uidValue);
        return new Uid(uidValue);
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
      ensureAuthManagerInitialized();
        LOG.ok("Entering delete with ObjectClass: {0}, UID: {1}", objectClass.getObjectClassValue(), uid.getUidValue());

        if (!objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new UnsupportedOperationException("Delete operation is not supported for object class: " + objectClass.getObjectClassValue());
        }

        // Realizar la solicitud HTTP DELETE
        String endpoint = getConfiguration().getServiceAddress() + USERS_ENDPOINT + "/" + uid.getUidValue();
        HttpDelete request = new HttpDelete(endpoint);
        try {
            String response = callRequest(request);
            LOG.ok("Deleted user with UID: {0}", uid.getUidValue());
        } catch (Exception e) {
            LOG.error("Error deleting user", e);
            throw new ConnectorException("Error deleting user with UID: " + uid.getUidValue(), e);
        }
    }

    @Override
    public Uid addAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> valuesToAdd, OperationOptions options) {
      ensureAuthManagerInitialized();
        LOG.ok("Entering addAttributeValues with ObjectClass: {0}, UID: {1}", objectClass.getObjectClassValue(), uid.getUidValue());
    
        // Implementación específica para añadir valores a atributos multi-valor
        // Aquí puedes agregar la lógica para añadir roles u otros atributos
    
        // Por ahora, lanzamos una excepción indicando que no está implementado
        throw new UnsupportedOperationException("addAttributeValues operation is not implemented yet.");
    }
    
    @Override
    public Uid removeAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> valuesToRemove, OperationOptions options) {
      ensureAuthManagerInitialized();
        LOG.ok("Entering removeAttributeValues with ObjectClass: {0}, UID: {1}", objectClass.getObjectClassValue(), uid.getUidValue());
    
        // Implementación específica para eliminar valores de atributos multi-valor
        // Aquí puedes agregar la lógica para eliminar roles u otros atributos
    
        // Por ahora, lanzamos una excepción indicando que no está implementado
        throw new UnsupportedOperationException("removeAttributeValues operation is not implemented yet.");
    }

    // ==============================
    // Bloque de Definición de Esquema
    // ==============================

    @Override
    public org.identityconnectors.framework.common.objects.Schema schema() {
        LOG.ok("Construyendo el esquema del conector");
        SchemaBuilder schemaBuilder = new SchemaBuilder(RestUsersConnector.class);

        // Definir ObjectClass para usuarios
        ObjectClassInfoBuilder userObjClassBuilder = new ObjectClassInfoBuilder();
        userObjClassBuilder.setType(ObjectClass.ACCOUNT_NAME);

        // `ATTR_ID` es el identificador principal (uuid en DSpace)
        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_ID)
                .setRequired(true)
                .setCreateable(false)
                .setUpdateable(false)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_USERNAME)
                .setRequired(true)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_EMAIL)
                .setRequired(true)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_FIRST_NAME)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_LAST_NAME)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_CAN_LOG_IN)
                .setCreateable(false)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_LAST_ACTIVE)
                .setCreateable(false)
                .setUpdateable(false)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_REQUIRE_CERTIFICATE)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_NET_ID)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_SELF_REGISTERED)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_ALERT_EMBARGO)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_LANGUAGE)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_LICENSE_ACCEPTED)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_LICENSE_ACCEPTED_DATE)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_ORCID_SCOPE)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_ORCID)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        userObjClassBuilder.addAttributeInfo(
            AttributeInfoBuilder.define(ATTR_PHONE)
                .setCreateable(true)
                .setUpdateable(true)
                .setReadable(true)
                .build()
        );

        // Definir el ObjectClass de usuario en el esquema
        schemaBuilder.defineObjectClass(userObjClassBuilder.build());

        LOG.ok("Esquema del conector construido exitosamente");
        return schemaBuilder.build();
    }

    // ==============================
    // Bloque de Búsqueda y Consulta
    // ==============================

    @Override
    public FilterTranslator<RestUsersFilter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return new RestUsersFilterTranslator();
    }

    @Override
    public void executeQuery(ObjectClass objectClass, RestUsersFilter query, ResultsHandler handler, OperationOptions options) {
      ensureAuthManagerInitialized();
        LOG.ok("Executing query on ObjectClass: {0}", objectClass.getObjectClassValue());

        if (!objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new UnsupportedOperationException("Search operation is not supported for object class: " + objectClass.getObjectClassValue());
        }

        try {
            String endpoint = getConfiguration().getServiceAddress() + USERS_ENDPOINT;
            if (query != null && query.byUid != null) {
                // Búsqueda por UID específico
                endpoint += "/" + query.byUid;
                HttpGet request = new HttpGet(endpoint);
                JSONObject response = new JSONObject(callRequest(request));

                ConnectorObject connectorObject = convertUserToConnectorObject(response);
                handler.handle(connectorObject);
            } else {
                // Búsqueda general
                HttpGet request = new HttpGet(endpoint);
                handleUsers(request, handler, options);
            }
        } catch (Exception e) {
            LOG.error("Error executing query", e);
            throw new ConnectorException("Error executing query", e);
        }
    }

    // Método para manejar la respuesta de usuarios
    private void handleUsers(HttpGet request, ResultsHandler handler, OperationOptions options) throws IOException {
        String responseString = callRequest(request);
        JSONObject responseObject = new JSONObject(responseString);

        if (responseObject.has("_embedded")) {
            JSONObject embedded = responseObject.getJSONObject("_embedded");
            if (embedded.has("epersons")) {
                JSONArray users = embedded.getJSONArray("epersons");
                for (int i = 0; i < users.length(); i++) {
                    JSONObject user = users.getJSONObject(i);
                    ConnectorObject connectorObject = convertUserToConnectorObject(user);
                    boolean continueProcessing = handler.handle(connectorObject);
                    if (!continueProcessing) {
                        break;
                    }
                }
            }
        }
    }

    // ==============================
    // Método para convertir datos de usuario en un ConnectorObject
    // ==============================
    private ConnectorObject convertUserToConnectorObject(JSONObject user) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
    
        // Identificador principal del usuario (`uuid` en DSpace-CRIS)
        builder.setUid(new Uid(user.getString("uuid")));
    
        // `name` es el nombre o identificador del usuario, en este caso su email
        builder.setName(user.optString(ATTR_USERNAME, "unknown"));
    
        // Atributos directos del usuario
        addAttr(builder, ATTR_EMAIL, user.optString(ATTR_EMAIL, null));
        addAttr(builder, ATTR_CAN_LOG_IN, user.optBoolean(ATTR_CAN_LOG_IN, false));
        addAttr(builder, ATTR_LAST_ACTIVE, user.optString(ATTR_LAST_ACTIVE, null));
        addAttr(builder, ATTR_REQUIRE_CERTIFICATE, user.optBoolean(ATTR_REQUIRE_CERTIFICATE, false));
        addAttr(builder, ATTR_NET_ID, user.optString(ATTR_NET_ID, null));
        addAttr(builder, ATTR_SELF_REGISTERED, user.optBoolean(ATTR_SELF_REGISTERED, false));
    
        // Atributos adicionales en el campo `metadata` de la respuesta JSON
        if (user.has("metadata")) {
            JSONObject metadata = user.getJSONObject("metadata");
            
            addAttr(builder, ATTR_FIRST_NAME, getMetadataValue(metadata, ATTR_FIRST_NAME));
            addAttr(builder, ATTR_LAST_NAME, getMetadataValue(metadata, ATTR_LAST_NAME));
            addAttr(builder, ATTR_LANGUAGE, getMetadataValue(metadata, ATTR_LANGUAGE));
            addAttr(builder, ATTR_ALERT_EMBARGO, getMetadataValue(metadata, ATTR_ALERT_EMBARGO));
            addAttr(builder, ATTR_LICENSE_ACCEPTED, getMetadataValue(metadata, ATTR_LICENSE_ACCEPTED));
            addAttr(builder, ATTR_LICENSE_ACCEPTED_DATE, getMetadataValue(metadata, ATTR_LICENSE_ACCEPTED_DATE));
            addAttr(builder, ATTR_ORCID_SCOPE, getMetadataValue(metadata, ATTR_ORCID_SCOPE));
            addAttr(builder, ATTR_ORCID, getMetadataValue(metadata, ATTR_ORCID));
            addAttr(builder, ATTR_PHONE, getMetadataValue(metadata, ATTR_PHONE));
        }
    
        return builder.build();
    }

    private String getMetadataValue(JSONObject user, String key) {
        if (user.has("metadata")) {
            JSONObject metadata = user.getJSONObject("metadata");
            if (metadata.has(key)) {
                JSONArray values = metadata.getJSONArray(key);
                if (values.length() > 0) {
                    return values.getJSONObject(0).getString("value");
                }
            }
        }
        return null;
    }

    // ==============================
    // Bloque de Manejo de Solicitudes HTTP
    // ==============================

    protected JSONObject callRequest(ClassicHttpRequest request, JSONObject jsonObject) {
        ensureAuthManagerInitialized();
        request.setHeader("Authorization", "Bearer " + authManager.getJwtToken());
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "application/json");

        try {
            StringEntity entity = new StringEntity(jsonObject.toString(), ContentType.APPLICATION_JSON);

            if (request instanceof HttpEntityContainer) {
                ((HttpEntityContainer) request).setEntity(entity);
            } else {
                throw new ConnectorException("Request does not support entity");
            }

            try (CloseableHttpResponse response = getHttpClient().execute(request)) {
                processResponseErrors(response);
                String result = EntityUtils.toString(response.getEntity());
                return new JSONObject(result);
            }
        } catch (IOException | ParseException e) {
            LOG.error("Error executing request", e);
            throw new ConnectorException("Error executing request", e);
        }
    }

    protected String callRequest(ClassicHttpRequest request) {
        ensureAuthManagerInitialized();
        request.setHeader("Authorization", "Bearer " + authManager.getJwtToken());
        request.setHeader("Content-Type", "application/json");

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {

            processResponseErrors(response);
            return EntityUtils.toString(response.getEntity());

        } catch (IOException | ParseException e) {
            LOG.error("Error executing request", e);
            throw new ConnectorException("Error executing request", e);
        }
    }

    public void processResponseErrors(CloseableHttpResponse response) {
        int statusCode = response.getCode();
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
    
        String responseBody = null;
        try {
            responseBody = EntityUtils.toString(response.getEntity());
        } catch (IOException | ParseException e) {
            LOG.warn("Cannot read response body: {0}", e.getMessage());
        }
    
        String reasonPhrase = response.getReasonPhrase();
        String message = "HTTP error " + statusCode + ": " + reasonPhrase;
        if (responseBody != null) {
            message += ". Response body: " + responseBody;
        }
    
        LOG.error("{0}", message);
        // Manejo de errores según el código de estado
        switch (statusCode) {
            case 400:
                throw new ConnectorException(message);
            case 401:
            case 403:
                throw new PermissionDeniedException(message);
            case 404:
                throw new UnknownUidException(message);
            case 409:
                throw new AlreadyExistsException(message);
            default:
                throw new ConnectorException(message);
        }
    }
    

    // ==============================
    // Bloque de Prueba del Conector
    // ==============================

    @Override
    public void test() {
        ensureAuthManagerInitialized();
        LOG.ok("Iniciando prueba de conexión al servicio.");

        try {
            // Verificar que podemos obtener un token JWT
            String jwtToken = authManager.getJwtToken();
            LOG.ok("Token JWT obtenido exitosamente.");

            // Realizar una solicitud simple para verificar la conectividad
            String endpoint = getConfiguration().getServiceAddress() + USERS_ENDPOINT;
            HttpGet request = new HttpGet(endpoint);
            String response = callRequest(request);
            LOG.ok("Respuesta recibida durante la prueba: {0}", response);

            LOG.ok("Prueba de conexión exitosa.");
        } catch (Exception e) {
            LOG.error("Error durante la prueba de conexión", e);
            throw new ConnectorException("Error durante la prueba de conexión: " + e.getMessage(), e);
        }
    }
}