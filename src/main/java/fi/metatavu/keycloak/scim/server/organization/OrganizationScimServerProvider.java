package fi.metatavu.keycloak.scim.server.organization;

import org.keycloak.provider.Provider;
import org.keycloak.models.KeycloakSession;

public interface OrganizationScimServerProvider extends Provider {

  public OrganizationScimServer getScimServer(KeycloakSession session);

  /**
   * Checks whether an organization with the given ID exists.
   *
   * @param orgId the organization ID
   * @return true if the organization exists
   */
  public boolean organizationExists(String orgId);

  default void close() {}
}
