package org.sakaiproject.hierarchy.test.contextable;

import java.sql.SQLException;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.db.impl.BasicSqlService;
import org.sakaiproject.db.impl.SqlServiceTest;
import org.sakaiproject.hierarchy.api.HierarchyService;
import org.sakaiproject.hierarchy.impl.ContextableHierarchyServiceImpl;
import org.sakaiproject.hierarchy.impl.HierarchyServiceImpl;
import org.sakaiproject.hierarchy.impl.model.dao.HierarchyDAO;
import org.sakaiproject.hierarchy.test.ServiceProvider;
import org.sakaiproject.id.impl.UuidV4IdComponent;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.thread_local.impl.ThreadLocalComponent;

public class ContextableServiceProvider implements ServiceProvider
	{

		private BasicDataSource ds;
		private HierarchyService service;

		public void setUp()
		{

			ds = new BasicDataSource();
			ds.setDriverClassName("org.hsqldb.jdbcDriver");
			ds.setUsername("sa");
			ds.setPassword("");
			ds.setUrl("jdbc:hsqldb:mem:db");

			BasicSqlService sqlService = new SqlServiceTest();
			sqlService.setDefaultDataSource(ds);
			sqlService.setAutoDdl("true");
			sqlService.init();

			ThreadLocalManager threadLocalManager = new ThreadLocalComponent();

			HierarchyDAO dao = new HierarchyDAO();
			dao.setSqlService(sqlService);
			dao.setThreadLocalManager(threadLocalManager);
			dao.setIdmanager(new UuidV4IdComponent());
			dao.init();

			HierarchyServiceImpl impl = new HierarchyServiceImpl();
			impl.setHierarchyDao(dao);
			impl.init();
			
			ContextableHierarchyServiceImpl contextable = new ContextableHierarchyServiceImpl();
			contextable.setContext("/portal");
			contextable.setHierarchyService(impl);
			contextable.init();

			service = contextable;
		}

		public void tearDown() throws SQLException
		{
			ds.getConnection().prepareStatement("SHUTDOWN").execute();
			ds.close();
		}

		public HierarchyService getService() {
			return service;
		}
}