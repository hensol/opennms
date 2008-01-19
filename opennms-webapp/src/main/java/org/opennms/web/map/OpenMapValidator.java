package org.opennms.web.map;

import javax.servlet.http.HttpServletRequest;

import org.opennms.web.WebSecurityUtils;
import org.springframework.validation.Errors;

public class OpenMapValidator extends MapApplianceValidator {
	public boolean supports(Class aClass) {
		return aClass.equals(HttpServletRequest.class);
	}

	public void validate(Object o, Errors errors) {
		
		
		if (!action.equals(MapsConstants.OPENMAP_ACTION) ) 
			errors.rejectValue("Action", MapsConstants.OPENMAP_ACTION+"Failed" , null, "action should be " + MapsConstants.OPENMAP_ACTION); 
		String mapIdentificator = request.getParameter("MapId");
		if (mapIdentificator == null) 
			errors.rejectValue("MapId", MapsConstants.OPENMAP_ACTION+"Failed" , null, "HttpServletReqiest parameter MapId is required");
		
		try {
			WebSecurityUtils.safeParseInt(mapIdentificator) ;
		} catch (NumberFormatException e) {
			errors.rejectValue("MapId", MapsConstants.OPENMAP_ACTION+"Failed" , null, "MapId is not an Integer");
		}
			
	}
}
