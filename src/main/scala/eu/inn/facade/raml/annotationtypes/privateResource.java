package eu.inn.facade.raml.annotationtypes;


import com.mulesoft.raml1.java.parser.core.CustomType;
import eu.inn.facade.raml.RamlAnnotation;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class privateResource extends CustomType implements RamlAnnotation {

}
