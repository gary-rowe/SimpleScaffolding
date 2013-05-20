package uk.co.froot.example.dto.admin_user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

/**
 * <p>DTO provide the following to resources:</p>
 * <ul>
 * <li>Representation of user state</li>
 * </ul>
 *
 * @since 0.0.1
 *         
 */
@JsonRootName("admin_user")
public class AdminUser {

  @JsonProperty
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AdminUser user = (AdminUser) o;

    if (id != null ? !id.equals(user.id) : user.id != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }
}
