package eu.inn.facade.raml.annotationtypes;

import eu.inn.facade.raml.RamlAnnotation;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class rewrite extends conditionalAnnotation {
    private String uri;

    @XmlElement(name="uri")
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
