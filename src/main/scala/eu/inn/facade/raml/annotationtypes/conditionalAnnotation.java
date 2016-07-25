package eu.inn.facade.raml.annotationtypes;

import com.mulesoft.raml1.java.parser.core.CustomType;
import eu.inn.facade.raml.RamlAnnotation;

import javax.xml.bind.annotation.XmlElement;

public class conditionalAnnotation extends CustomType implements RamlAnnotation {

    private String predicate;

    @XmlElement(name = "if")
    public String getPredicate() {
        return predicate;
    }

    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }
}
