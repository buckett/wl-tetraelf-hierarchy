package org.sakaiproject.portal.charon.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.hierarchy.api.PortalHierarchyService;
import org.sakaiproject.hierarchy.api.model.PortalNode;
import org.sakaiproject.portal.api.Portal;
import org.sakaiproject.portal.api.PortalHandlerException;
import org.sakaiproject.portal.api.PortalRenderContext;
import org.sakaiproject.portal.api.StoredState;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.thread_local.cover.ThreadLocalManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.ToolException;
import org.sakaiproject.util.Web;

public class HierarchyHandler extends SiteHandler {
	
	public final static String INCLUDE_HIERARCHY_PAGE_NAV = "include-hierarchy-page-nav";
	
	private static Log log = LogFactory.getLog(HierarchyHandler.class);
	private SiteService siteService;
	private PortalHierarchyService portalHierarchyService;
	
	public HierarchyHandler(SiteService siteService, PortalHierarchyService portalHierarchyService) {
		this.siteService = siteService;
		this.portalHierarchyService = portalHierarchyService;
		setUrlFragment("hierarchy");
	}
	
	public int doGet(String[] parts, HttpServletRequest req, HttpServletResponse res,
			Session session) throws PortalHandlerException
	{
		// If we are specified (/portal/hierarchy)
		if ( (parts.length >= 2) && parts[1].equals(getUrlFragment()))
		{
			return doFindSite(parts, 2, req, res, session);
		}
		// If we have an extra slash (/portal/)
		else if (parts.length > 0)
		{
			return doFindSite(parts, 1, req, res, session);
		}
		// Default handling (/portal)
		else if (parts.length == 0)
		{
			return doFindSite(parts, 0, req, res, session);
		}
		else
		{
			return NEXT;
		}
	}

	// / - root site / default page
	// /page/site - root site / page site
	// /college - college site / default page
	// /college/page/site - college site / page site
	// /asdasd/asdasd - Error 
	
	private int doFindSite(String[] parts, int start, HttpServletRequest req,
			HttpServletResponse res, Session session)
			throws PortalHandlerException {
		try
		{
			PortalHierarchyService phs = org.sakaiproject.hierarchy.cover.PortalHierarchyService.getInstance();
			PortalNode node = null;
			Site site = null;
			int hierarchyPartNo = parts.length;
			
			// Default site.
			if (hierarchyPartNo == start || parts[start].equals("page")) {
				node = phs.getNode(null);
				hierarchyPartNo = start-1;
			} else {
				for (; node == null && hierarchyPartNo >= start; hierarchyPartNo--) {
					String hierarchyPath = buildPath(parts, start, hierarchyPartNo);
					log.debug("Looking for: "+ hierarchyPath);
					node = phs.getNode(hierarchyPath);
				}
			}
			
			if (node == null)
			{
				if (parts.length > 2)
				{
					try
					{
						site = portal.getSiteHelper().getSiteVisit(parts[2]);
						hierarchyPartNo = 2;
					}
					catch (IdUnusedException iuue)
					{
						portal.doError(req, res, session, Portal.ERROR_SITE);
					}
					catch (PermissionException pe)
					{
						if (session.getUserId() == null)
						{
							StoredState ss = portalService.newStoredState("directtool", "tool");
							ss.setRequest(req);
							ss.setToolContextPath(req.getServletPath());
							portalService.setStoredState(ss);
							portal.doLogin(req, res, session, req.getPathInfo(), false);
						}
						else
						{
							portal.doError(req, res, session, Portal.ERROR_SITE);
						}
					}
				}
			}
			else
			{
				phs.setCurrentPortalPath(node.getPath());
				site = node.getSite();
			}
			

			
			String pageId = null;
			if (parts.length >= hierarchyPartNo+2 && "page".equals(parts[hierarchyPartNo+1])){
				pageId = parts[hierarchyPartNo+2];
			}
			
			log.debug("siteId: "+ site.getId()+ " pageId: "+ pageId);
			if (node == null) {
				super.doSite(req, res, session, site.getId(), pageId, null);
			} else {
				doSite(req, res, session, site, pageId, req.getContextPath()
						+ req.getServletPath()+node.getPath(), node);
			}
			return END;
		}
		catch (Exception ex)
		{
			throw new PortalHandlerException(ex);
		}
	}

	/**
	 * Builds a hierarchy path from subset of the parts supplied.
	 * @return The built string.
	 */
	private String buildPath(String[] parts, int start, int stop)
	{
		StringBuffer hierarchyPath = new StringBuffer();
		for (int partNo = start; partNo < stop; partNo++) {
			hierarchyPath.append("/");
			hierarchyPath.append(parts[partNo]);
		}
		return hierarchyPath.toString();
	}
	

	public void doSite(HttpServletRequest req, HttpServletResponse res, Session session,
			Site site, String pageId, String toolContextPath, PortalNode node) throws ToolException,
			IOException
	{
		

		Site hierarchySite = null;
		
		// default site if not set
		if (site == null)
		{
			portal.doError(req, res, session, Portal.ERROR_SITE);
			return;
		}

		if (node !=null && !node.canView())
		{
			// if not logged in, give them a chance
			if (session.getUserId() == null)
			{
				StoredState ss = portalService.newStoredState("directtool", "tool");
				ss.setRequest(req);
				ss.setToolContextPath(toolContextPath);
				portalService.setStoredState(ss);
				portal.doLogin(req, res, session, req.getPathInfo(), false);
			}
			else
			{
				portal.doError(req, res, session, Portal.ERROR_SITE);
			}
			return;
		}
		try
		{
			hierarchySite = siteService.getSite("!hierarchy");
		}
		catch (IdUnusedException e)
		{
			log.warn("Hierarchy site not found.");
		}

		// find the page, or use the first page if pageId not found
		SitePage page = lookupSitePage(pageId, site);
		if (page == null && pageId != null && node != null)
		{
			// Look in the hierarchy site.
			page = lookupSitePage(pageId, hierarchySite);
			if (page != null) 
			{
				// Fix up the skin.
				page = new AdoptedSitePage(site, page);
			}

		}
		
		if (page == null)
		{
			List pages =  portal.getSiteHelper().getPermittedPagesInOrder(site);
			if (!pages.isEmpty())
			{
				page = (SitePage) pages.get(0);
			}
		}
		if (page == null)
		{
			portal.doError(req, res, session, Portal.ERROR_SITE);
			return;
		}


		ThreadLocalManager.set("sakai:portal:node", node);


		// form a context sensitive title
		String title = ServerConfigurationService.getString("ui.service") + " : "
				+ site.getTitle() + " : " + page.getTitle();

		// start the response
		String siteType = portal.calcSiteType(site.getId());
		PortalRenderContext rcontext = portal.startPageContext(siteType, title, site
				.getSkin(), req);

		// Include normal site nav details.
		includeSiteNav(rcontext, req, session, site.getId());
		
		String prefix = getUrlFragment();
		String siteUrl = node.getPath();
		if (siteUrl.endsWith("/")) 
		{
			siteUrl = siteUrl.substring(0, siteUrl.length()-1);
		}

		

		if (hierarchySite != null)
		{
			includeHierarchyNav(rcontext, req, session, site, page, toolContextPath, prefix, siteUrl, hierarchySite, node);
		}
		includeWorksite(rcontext, res, req, session, site, page, toolContextPath, prefix);

		portal.includeBottom(rcontext);

		// end the response
		portal.sendResponse(rcontext, res, "site", null);
		StoredState ss = portalService.getStoredState();
		if (ss != null && toolContextPath.equals(ss.getToolContextPath()))
		{
			// This request is the destination of the request
			portalService.setStoredState(null);
		}
	}
	
	protected void includeHierarchyNav(PortalRenderContext rcontext, HttpServletRequest req,
			Session session, Site site, SitePage page, String toolContextPath, String prefix, String siteUrl, Site hierarchySite, PortalNode node)
	{
			boolean loggedIn = session.getUserId() != null;


			String accessibilityURL = ServerConfigurationService
					.getString("accessibility.url");
			rcontext.put("siteNavHasAccessibilityURL", Boolean
					.valueOf((accessibilityURL != null && accessibilityURL != "")));
			rcontext.put("siteNavAccessibilityURL", accessibilityURL);

			rcontext.put("siteNavLoggedIn", Boolean.valueOf(loggedIn));
			
			String cssClass = (site.getType() != null) ? "siteNavWrap " + site.getType()
					: "siteNavWrap";

			rcontext.put("tabsCssClass", cssClass);

			try {
				includeLogo(rcontext, req, session, site.getId());
				includeHierarchy(rcontext, req, session, site, page, toolContextPath, prefix, siteUrl, hierarchySite, node);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	private void includeHierarchy(PortalRenderContext rcontext, HttpServletRequest req, Session session, Site site, SitePage page, String toolContextPath, String portalPrefix, String siteUrl, Site hierarchySite, PortalNode node) {
		// Need to get list of parents

		List<PortalNode> parentNodes = portalHierarchyService.getNodesFromRoot(node.getId());

		boolean loggedIn = session.getUserId() != null;
		boolean resetTools = "true".equals(ServerConfigurationService.getString(Portal.CONFIG_AUTO_RESET));

		List<Site> parentSites = new ArrayList<Site>(parentNodes.size());
		for (PortalNode parentNode: parentNodes) {
			parentSites.add(parentNode.getSite());
		}
		String currentSiteId = node.getSite().getId();
		Site myWorkspace = portal.getSiteHelper().getMyWorkspace(session);
		String myWorkspaceId = null;
		if (myWorkspace != null) {
			myWorkspaceId = myWorkspace.getId();
		}
		
		List<Map> parentSiteMaps = portal.getSiteHelper().convertSitesToMaps(req, parentSites, getUrlFragment(), currentSiteId, myWorkspaceId, false, false, resetTools, false, null, loggedIn);
		rcontext.put("parents", parentSiteMaps);

		// Details of children.
		// List of site.visit but also display joinable.

		List<Site> childSites = new ArrayList<Site>();

		for (PortalNode currentChild : portalHierarchyService.getNodeChildren(node.getId())) {
			if (currentChild.canView()) {
				childSites.add(currentChild.getSite());
			}
		}
		List<Map> childSiteMaps = portal.getSiteHelper().convertSitesToMaps(req, childSites, getUrlFragment(), currentSiteId, myWorkspaceId, false, false, resetTools, false, null, loggedIn);
		rcontext.put("children", childSiteMaps);

		String pageUrl = Web.returnUrl(req, "/" + portalPrefix + siteUrl
				+ "/page/");
		String toolUrl = Web.returnUrl(req, "/" + portalPrefix 
				+ Web.escapeUrl(portal.getSiteHelper().getSiteEffectiveId(site)));
		String pagePopupUrl = Web.returnUrl(req, "/page/");

		Map hierarchyPages = portal.getSiteHelper().pageListToMap(req, loggedIn, hierarchySite, page, toolUrl, portalPrefix, true, resetTools, false);
		//Map hierarchyPages = pageListToMap(req, loggedIn, hierarchySite, page, toolUrl, portalPrefix, true, resetTools, false);
		rcontext.put("hierarchyPages", hierarchyPages);

		// What todo if you can't see current site?

	}

	private Map<String, Object> getUnknownSite(PortalNode currentNode) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("title", "Unknown Site");
		map.put("url", getNodeURL(currentNode));
		return null;
	}
	
	private Map<String, Object> convertToMap(PortalNode currentNode) {
		Map<String, Object> siteDetails = new HashMap<String, Object>();
		if (currentNode.canView()) {
			Site currentSite = currentNode.getSite();
			siteDetails.put("url", getNodeURL(currentNode));
			siteDetails.put("title", Web.escapeHtml(currentSite.getTitle()));
			siteDetails.put("path", currentNode.getPath());
			siteDetails.put("shortDescription", (currentSite.getShortDescription()== null)?null:Web.escapeHtml(currentSite.getShortDescription()));
			return siteDetails;
		}

		return null;
	}
	
	private String getNodeURL(PortalNode node) {
		return ServerConfigurationService.getPortalUrl()+ "/"+ getUrlFragment() + Web.escapeUrl(node.getPath());
	}
	
	public SitePage lookupSitePage(String pageId, Site site)
	{
		// Make sure we have some permitted pages
		List pages = portal.getSiteHelper().getPermittedPagesInOrder(site);
		if (pages.isEmpty()) return null;
		SitePage page = site.getPage(pageId);
		if (page == null)
		{
			page = portal.getSiteHelper().lookupAliasToPage(pageId, site);
		}
		if (page != null) {

			// Make sure that they user has permission for the page.
			// If the page is not in the permitted list go to the first
			// page.
			for (Iterator i = pages.iterator(); i.hasNext();)
			{
				SitePage p = (SitePage) i.next();
				if (p.getId().equals(page.getId())) return page;
			}
		}

		return null;
	}
	
//	public void includeWorksite(PortalRenderContext rcontext, HttpServletResponse res,
//			HttpServletRequest req, Session session, Site site, SitePage page,
//			String toolContextPath, String portalPrefix, String siteUrl) throws IOException
//	{
//		if (rcontext.uses(INCLUDE_WORKSITE))
//		{
//
//			// add the page navigation with presence
//			boolean loggedIn = session.getUserId() != null;
//			String pageUrl = Web.returnUrl(req, "/" + portalPrefix 
//					+ "/page/");
//			String toolUrl = Web.returnUrl(req, "/" + portalPrefix 
//					+ Web.escapeUrl(portal.getSiteHelper().getSiteEffectiveId(site)));
//			String pagePopupUrl = Web.returnUrl(req, "/page/");
//			
//			List pageMap = convertPagesToMap( site, page, 
//				portalPrefix, 
//				/* doPages */true,
//				/* resetTools */"true".equals(ServerConfigurationService
//						.getString(Portal.CONFIG_AUTO_RESET)),
//				/* includeSummary */false, pageUrl, toolUrl, pagePopupUrl);
//			Map sitePages = new HashMap();
//			sitePages.put("pageNaveToolsCount", pageMap.size());
//			sitePages.put("pageNavTools", pageMap);
//			rcontext.put("sitePages", sitePages);
//
//			// add the page
//			includePage(rcontext, res, req, session, page, toolContextPath, "content");
//		}
//
//	}
//	
//	protected void includePageList(PortalRenderContext rcontext, HttpServletRequest req,
//			Session session, Site site, SitePage page, String toolContextPath,
//			String portalPrefix, boolean doPages, boolean resetTools,
//			boolean includeSummary) throws IOException
//	{
//		boolean loggedIn = session.getUserId() != null;
//
//		String pageUrl = Web.returnUrl(req, "/" + portalPrefix 
//				+ "/page/");
//		String toolUrl = Web.returnUrl(req, "/" + portalPrefix 
//				+ Web.escapeUrl(portal.getSiteHelper().getSiteEffectiveId(site)));
//		if (resetTools)
//		{
//			toolUrl = toolUrl + "/tool-reset/";
//		}
//		else
//		{
//			toolUrl = toolUrl + "/tool/";
//		}
//
//		String pagePopupUrl = Web.returnUrl(req, "/page/");
//		
//		if (rcontext.uses(INCLUDE_PAGE_NAV))
//		{
//			boolean showHelp = ServerConfigurationService.getBoolean("display.help.menu",
//					true);
//			String iconUrl = site.getIconUrlFull();
//			boolean published = site.isPublished();
//			String type = site.getType();
//
//			rcontext.put("pageNavPublished", Boolean.valueOf(published));
//			rcontext.put("pageNavType", type);
//			rcontext.put("pageNavIconUrl", iconUrl);
//			// rcontext.put("pageNavSitToolsHead",
//			// Web.escapeHtml(rb.getString("sit_toolshead")));
//
//			// order the pages based on their tools and the tool order for the
//			// site type
//			// List pages = site.getOrderedPages();
//			List<Map> l = convertPagesToMap(site, page, portalPrefix, doPages,
//					resetTools, includeSummary, pageUrl, toolUrl, pagePopupUrl);
//			rcontext.put("pageNavTools", l);
//
//			String helpUrl = ServerConfigurationService.getHelpUrl(null);
//			rcontext.put("pageNavShowHelp", Boolean.valueOf(showHelp));
//			rcontext.put("pageNavHelpUrl", helpUrl);
//
//			// rcontext.put("pageNavSitContentshead",
//			// Web.escapeHtml(rb.getString("sit_contentshead")));
//
//			// Handle Presense
//			boolean showPresence = ServerConfigurationService.getBoolean(
//					"display.users.present", true);
//			String presenceUrl = Web.returnUrl(req, "/presence/"
//					+ Web.escapeUrl(site.getId()));
//
//			// rcontext.put("pageNavSitPresenceTitle",
//			// Web.escapeHtml(rb.getString("sit_presencetitle")));
//			// rcontext.put("pageNavSitPresenceFrameTitle",
//			// Web.escapeHtml(rb.getString("sit_presenceiframetit")));
//			rcontext.put("pageNavShowPresenceLoggedIn", Boolean.valueOf(showPresence
//					&& loggedIn));
//			rcontext.put("pageNavPresenceUrl", presenceUrl);
//		}
//
//	}
//
//	protected List<Map> convertPagesToMap(Site site, SitePage page,
//			String portalPrefix, boolean doPages, boolean resetTools,
//			boolean includeSummary, String pageUrl, String toolUrl,
//			String pagePopupUrl) {
//		List pages = portal.getSiteHelper().getPermittedPagesInOrder(site);
//
//		List<Map> l = new ArrayList<Map>();
//		for (Iterator i = pages.iterator(); i.hasNext();)
//		{
//
//			SitePage p = (SitePage) i.next();
//			List pTools = p.getTools();
//
//			boolean current = (page != null && p.getId().equals(page.getId()) && !p
//					.isPopUp());
//			String pagerefUrl = pageUrl + Web.escapeUrl(/*(p.getName() != null)?p.getName():*/p.getId());
//			if (resetTools)
//			{
//				pagerefUrl = pagerefUrl.replaceFirst("/" + portalPrefix + "/", "/"
//						+ portalPrefix + "-reset/");
//			}
//
//			if (doPages || p.isPopUp())
//			{
//				Map<String, Object> m = new HashMap<String, Object>();
//				m.put("isPage", Boolean.valueOf(true));
//				m.put("current", Boolean.valueOf(current));
//				m.put("ispopup", Boolean.valueOf(p.isPopUp()));
//				m.put("pagePopupUrl", pagePopupUrl);
//				m.put("pageTitle", Web.escapeHtml(p.getTitle()));
//				m.put("jsPageTitle", Web.escapeJavascript(p.getTitle()));
//				m.put("pageId", Web.escapeUrl(p.getId()));
//				m.put("jsPageId", Web.escapeJavascript(p.getId()));
//				m.put("pageRefUrl", pagerefUrl);
//				l.add(m);
//				continue;
//			}
//
//			// Loop through the tools again and Unroll the tools
//			Iterator iPt = pTools.iterator();
//
//			while (iPt.hasNext())
//			{
//				ToolConfiguration placement = (ToolConfiguration) iPt.next();
//
//				String toolrefUrl = toolUrl + Web.escapeUrl(placement.getId());
//
//				Map<String, Object> m = new HashMap<String, Object>();
//				m.put("isPage", Boolean.valueOf(false));
//				m.put("toolId", Web.escapeUrl(placement.getId()));
//				m.put("jsToolId", Web.escapeJavascript(placement.getId()));
//				m.put("toolRegistryId", placement.getToolId());
//				m.put("toolTitle", Web.escapeHtml(placement.getTitle()));
//				m.put("jsToolTitle", Web.escapeJavascript(placement.getTitle()));
//				m.put("toolrefUrl", toolrefUrl);
//				l.add(m);
//			}
//
//		}
//		return l;
//	}


}
