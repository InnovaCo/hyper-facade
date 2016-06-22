package eu.inn.facade.raml.annotationtypes;


import com.mulesoft.raml1.java.parser.core.CustomType;
import eu.inn.facade.raml.RamlAnnotation;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class private_ extends CustomType implements RamlAnnotation {

    private String[] allowedUsers = new String[0];
    private String[] allowedRoles = new String[0];
    private Boolean arePrivateNetworksAllowed = true;

    @XmlElement(name = "allow-users")
    public String[] getAllowedUsers() {
        return allowedUsers;
    }

    public void setAllowedUsers(String[] allowedUsers) {
        this.allowedUsers = allowedUsers;
    }

    @XmlElement(name = "allow-roles")
    public String[] getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(String[] allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    @XmlElement(name = "allow-private-networks")
    public Boolean getArePrivateNetworksAllowed() {
        return arePrivateNetworksAllowed;
    }

    public void setArePrivateNetworksAllowed(Boolean arePrivateNetworksAllowed) {
        this.arePrivateNetworksAllowed = arePrivateNetworksAllowed;
    }
}
