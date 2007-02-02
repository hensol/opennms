//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2006 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
//      OpenNMS Licensing       <license@opennms.org>
//      http://www.opennms.org/
//      http://www.opennms.com/
//
package org.opennms.web.map;

/*
 * Created on 8-giu-2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Category;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.web.acegisecurity.Authentication;
import org.opennms.web.element.NetworkElementFactory;
import org.opennms.web.map.db.MapMenu;
import org.opennms.web.map.view.Manager;
import org.opennms.web.map.view.VElement;
import org.opennms.web.map.view.VLink;
import org.opennms.web.map.view.VMap;


import java.util.*;

/**
 * @author mmigliore
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class ModifyMapServlet extends HttpServlet {

	

	


	Category log;

	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		ThreadCategory.setPrefix(MapsConstants.LOG4J_CATEGORY);
		log = ThreadCategory.getInstance(this.getClass());
		String elems = request.getParameter("elems");
		String action = request.getParameter("action");
		log.debug("Received action="+action+" elems="+elems);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response
				.getOutputStream()));
		String strToSend = "";
		try {
			HttpSession session = request.getSession(false);
			Manager m = null;
			if(session!=null){
				m = (Manager) session.getAttribute("manager");
				log.debug("Got manager from session: "+m);
			}
			String refreshTime = (String)session.getAttribute("refreshTime");
			int refreshtime = 300; 
			if (refreshTime != null) {
				refreshtime = Integer.parseInt(refreshTime)*60;
			}
			m.startSession();
			VMap map = null;
			List velems = new ArrayList();
//			List links = new ArrayList();

			if (session != null) {
				map = (VMap) session.getAttribute("sessionMap");
				if (map != null) {
					strToSend = action + "OK";
					Integer[] nodeids = null;
					String TYPE = VElement.NODE_TYPE;

					boolean actionfound = false;
					
					if (action.equals(MapsConstants.ADDNODES_ACTION)) {
						actionfound = true;
						String[] snodeids = elems.split(",");
						nodeids = new Integer[snodeids.length];
						for (int i = 0; i<snodeids.length;i++) {
							nodeids[i] = new Integer(snodeids[i]);
						}
					}

					if (action.equals(MapsConstants.ADDRANGE_ACTION)) {
						actionfound = true;
						nodeids = (Integer[]) NetworkElementFactory.getNodeIdsWithIpLike(elems).toArray(new Integer[0]);
					}

					if (action.equals(MapsConstants.ADDNODES_NEIG_ACTION)) {
						actionfound = true;
						nodeids = (Integer[]) NetworkElementFactory.getLinkedNodeIdOnNode(Integer.parseInt(elems)).toArray(new Integer[0]);
					}

					if (action.equals(MapsConstants.ADDNODES_WITH_NEIG_ACTION)) {
						actionfound = true;
						Set linkednodeids = NetworkElementFactory.getLinkedNodeIdOnNode(Integer.parseInt(elems));
						linkednodeids.add(new Integer(elems));
						nodeids = (Integer[]) linkednodeids.toArray(new Integer[0]);
					} 
					
					if (action.equals(MapsConstants.ADDMAPS_ACTION)) {
						actionfound = true;
						TYPE = VElement.MAP_TYPE;
						String[] snodeids = elems.split(",");
						nodeids = new Integer[snodeids.length];
						for (int i = 0; i<snodeids.length;i++) {
							nodeids[i] = new Integer(snodeids[i]);
						}
					}

					// response for addElement
					if (actionfound) {
						log.debug("Before Checking map contains elems");
						
						for (int i = 0; i < nodeids.length; i++) {
							int elemId = nodeids[i].intValue();
							if (map.containsElement(elemId, TYPE)) {
								log.debug("Action: " + action + " . Map Contains Element: " + elemId+TYPE);
								continue;
								
							}
							if (TYPE.equals(VElement.MAP_TYPE) && m.foundLoopOnMaps(map,elemId)) {
								strToSend += "&loopfound" + elemId;
								log.debug("Action: " + action + " . Map " + map.getName()+ "Loop Found On Element: " + elemId+TYPE);
								continue;
							}
							VElement curVElem = m.newElement(map.getId(),
									elemId, TYPE);
							//set real-time data to -1 to force refresh always
							curVElem.setSeverity(-1);
							curVElem.setStatus(-1);
							curVElem.setRtc(-1);
							velems.add(curVElem);
						} // end for
						
						log.debug("After Checking map contains elems");
						log.debug("Before RefreshElements");
						velems = m.refreshElements((VElement[]) velems.toArray(new VElement[0]));
						log.debug("After RefreshElements");
						log.debug("Before getting/adding links");
						//List vElemLinks = m.getLinks(map.getAllElements());
						if (velems != null) {
							Iterator ite = velems.iterator();
							while (ite.hasNext()) {
								// take the VElement object
								VElement ve = (VElement) ite.next();
								// Get the link between ma objects and new Element
								//List vElemLinks = new ArrayList();
								List vElemLinks = m.getLinksOnElem(map.getAllElements(), ve);
								// add MapElement to Map
								map.addElement(ve);
								// Add correpondant Links to Map
								map.addLinks((VLink[]) vElemLinks
										.toArray(new VLink[0]));
								// Add String to return to client
								strToSend += "&" + ve.getId() + ve.getType() + "+"
										+ ve.getIcon() + "+" + ve.getLabel();
								strToSend += "+" + ve.getRtc() + "+"
										+ ve.getStatus() + "+" + ve.getSeverity();
								// add String to return containing Links
								if (vElemLinks != null) {
									Iterator sub_ite = vElemLinks.iterator();
									while (sub_ite.hasNext()) {
										VLink vl = (VLink) sub_ite.next();
										strToSend += "&" + vl.getFirst().getId()
												+ vl.getFirst().getType() + "+"
												+ vl.getSecond().getId()
												+ vl.getSecond().getType()+"+"+vl.getTypology()+"+"+vl.getStatus();
									}
								}
							} // end cicle on element found
						}
						
						log.debug("After getting/adding links");
						//end if velement to add
					
					}   // and first if action found	
					
					if (!actionfound) {
						if (action.equals(MapsConstants.DELETENODES_ACTION)) {
							actionfound = true;
							TYPE = VElement.NODE_TYPE;
							String[] snodeids = elems.split(",");
							nodeids = new Integer[snodeids.length];
							for (int i = 0; i<snodeids.length;i++) {
								nodeids[i] = new Integer(snodeids[i]);
							}
						}
						
						if (action.equals(MapsConstants.DELETEMAPS_ACTION)) {
							actionfound = true;
							TYPE = VElement.MAP_TYPE;
							String[] snodeids = elems.split(",");
							nodeids = new Integer[snodeids.length];
							for (int i = 0; i<snodeids.length;i++) {
								nodeids[i] = new Integer(snodeids[i]);
							}
						}

						if (actionfound) {
	
							for (int i = 0; i < nodeids.length; i++) {
								int elemId = nodeids[i].intValue();
								if (map.containsElement(elemId, TYPE)){
									map.removeLinksOnElementList(elemId,TYPE);
									velems.add(map.removeElement(elemId,TYPE));
									strToSend += "&" + elemId + TYPE;
								}
							}
						} 
					}

					if (action.equals(MapsConstants.REFRESH_ACTION)) {
						actionfound = true;
						// First refresh Element objects
						VElement[] velements=(VElement[]) m.refreshElements(map.getAllElements()).toArray(new VElement[0]);
						//checks for only changed velements 
						if (velements != null) {
							for(int k=0; k<velements.length;k++){
								VElement ve = velements[k];
								strToSend += "&" + ve.getId() + ve.getType() + "+"
										+ ve.getIcon() + "+" + ve.getLabel();
								strToSend += "+" + ve.getRtc() + "+"
										+ ve.getStatus() + "+" + ve.getSeverity();
								map.addElement(ve);
							}
						}

						// Second Refresh Link Object on Map
						// Now is done using a very simple way
						// but really it's slow
						// the alternativ is anyway to analize all 
						// links, 1 against other.
						// So with this solution more traffic
						// less stress on server
						// more work on client
						
						// We are waiting to attempt to mapd
						map.removeAllLinks();

						// get all links on map
						//List links = null;
						List links = m.getLinks(map.getAllElements());

						// add links to map
						map.addLinks((VLink[]) links.toArray(new VLink[0]));

						// write to client
						if (links != null) {
							Iterator ite = links.iterator();
							while (ite.hasNext()) {
								VLink vl = (VLink) ite.next();
									strToSend += "&" + vl.getFirst().getId()
									+ vl.getFirst().getType() + "+"
									+ vl.getSecond().getId()
									+ vl.getSecond().getType()+"+"+vl.getTypology()+"+"+vl.getStatus();
							}
						} 
						
					} 
					
					if (action.equals(MapsConstants.RELOAD_ACTION)) {
						actionfound = true;
						// First refresh Element objects
						map = m.reloadMap(map);
						VElement[] velements=map.getAllElements();
						
						//checks for only changed velements 
						if (velements != null) {
							for(int k=0; k<velements.length;k++){
								VElement ve = velements[k];
								strToSend += "&" + ve.getId() + ve.getType() + "+"
										+ ve.getIcon() + "+" + ve.getLabel();
								strToSend += "+" + ve.getRtc() + "+"
										+ ve.getStatus() + "+" + ve.getSeverity()+ "+" + ve.getX()+ "+" + ve.getY();
							}
						}

						// Second Refresh Link Object on Map
						// Now is done using a very simple way
						// but really it's slow
						// the alternativ is anyway to analize all 
						// links, 1 against other.
						// So with this solution more traffic
						// less stress on server
						// more work on client
						
						// We are waiting to attempt to mapd
						map.removeAllLinks();

						// get all links on map
						//List links = null;
						List links = m.getLinks(velements);

						// add links to map
						map.addLinks((VLink[]) links.toArray(new VLink[0]));

						// write to client
						if (links != null) {
							Iterator ite = links.iterator();
							while (ite.hasNext()) {
								VLink vl = (VLink) ite.next();
									strToSend += "&" + vl.getFirst().getId()
									+ vl.getFirst().getType() + "+"
									+ vl.getSecond().getId()
									+ vl.getSecond().getType()+"+"+vl.getTypology()+"+"+vl.getStatus();
							}
						} 
						
					}
					if (action.equals(MapsConstants.CLEAR_ACTION)) {
						actionfound = true;
						map.removeAllLinks();
						map.removeAllElements();
					}

					if (actionfound) {
						session.setAttribute("sessionMap",map);
					} else {
						throw new Exception("action " + action + " not exists");
					}

				} else {
					throw new Exception("Attribute session sessionMap is null");
				}
			} else {
				throw new Exception("HttpSession not initialized");
			}
			m.endSession();
			
		} catch (Exception e) {
			strToSend = action + "Failed";
			log.error("Exception catch " + e);
			StackTraceElement[] ste = e.getStackTrace();
			for(int k=0; k<ste.length; k++){
				log.error(ste[k].toString());
			}
		} finally {
			bw.write(strToSend);
			bw.close();
			log.info("Sending response to the client '" + strToSend + "'");
		}
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doPost(request, response);
	}

}