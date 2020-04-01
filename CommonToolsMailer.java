/** ============================================================================
* *                   Copyright (c) Siemens PLM Software Inc 2012
*                     Unpublished - All rights reserved
* ===================================================================================
* File Description: CommonToolsMailer.java
* 					This file is written to send Notifications to roles configured in the settings file on SVN Commit
* ===================================================================================
* Revision History
* ===================================================================================
* Date             Name                Description of Change
* -----------      ---------------     ---------------------
* 14/01/2020       Rameshwari        Changed the code to read the smtp port from the settings file
* $HISTORY$
* ================================================================================
*/
package com.polarion.aks.commontools;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.xml.rpc.ServiceException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;


public class CommonToolsMailer {
	
	private final String repos_path;
	private final String revision;
	private final String project_name;
	private final String pathSeparator;
	private static final String DATE_FORMAT = "E, dd MMM yyyy HH:mm:ss z";
	final static Logger logger = Logger.getLogger(CommonToolsMailer.class);
	
	public CommonToolsMailer(String repo, String rev, String reponame, String settingsfile, String pathSeparator) throws IOException, SVNException, ServiceException
	{
		super();
		this.revision = rev;
		this.repos_path = repo;
		this.project_name = reponame;
		this.pathSeparator = pathSeparator;
		getData(repos_path, revision, project_name, settingsfile,pathSeparator);
		
	}
	
	public void getData(String repo_path, String rev, String project_name, String settingsfile,String pathSeparator) throws IOException, SVNException, ServiceException
	{
		String commitMsg = null;
		String author = null;
		Date commitdate = null;
		String type = null;
		SVNNodeKind kind = null;
		String action = null;
		
		//ArrayList<String> changedSet = new ArrayList<String>();
		Map<SVNLogEntryPath, String> map = new HashMap<SVNLogEntryPath, String>();
		
		FileInputStream stream = new FileInputStream(settingsfile);
		Properties env = new Properties();
		env.load(stream);
		
		Long revision = Long.parseLong((String)rev);
		SVNProperties properties = null;
		
		SVNRepository svnRepo = svnAuthenticate(env, repo_path);
		Collection<String> logEntries = null;
		logEntries = svnRepo.log(new String[]{""}, null, revision, revision, true, true);
		
		Iterator it = logEntries.iterator();
		while(it.hasNext())
		{
			SVNLogEntry logEntry = (SVNLogEntry) it.next();
			commitMsg = logEntry.getMessage();
			author = logEntry.getAuthor();
			commitdate = logEntry.getDate();

			Set changedPathsSet = logEntry.getChangedPaths().keySet();
			
			Iterator changedPaths = changedPathsSet.iterator( );
			while(changedPaths.hasNext())
			{
				SVNLogEntryPath entryPath = ( SVNLogEntryPath )logEntry.getChangedPaths().get(changedPaths.next());
			
				type = Character.toString(entryPath.getType());
				
				if(type.contentEquals("A"))
				{
					action = "Added";
				}
				else if(type.contentEquals("D"))
				{
					action = "Deleted";
				}
				else if(type.contentEquals("M"))
				{
					action = "Modified";
				}
				else
				{
					action = "Replaced";
				}
				
				map.put(entryPath, action);
			}
		}
		
		sendMails(project_name, rev, commitMsg, author, commitdate, map, settingsfile,pathSeparator);
		
	}

	public static void sendMails(String project_name, String revision, String commitMsg, String user, Date commitdate, Map<SVNLogEntryPath, String> map, String settingsfile,String pathSeparator) throws IOException, SVNException, ServiceException
	{
		FileInputStream stream = new FileInputStream(settingsfile);
		Properties env = new Properties();
		int count = 1;

		String[] comMsgArray = commitMsg.split("\r\n|\r|\n");
		Properties property = new Properties();
		env.load(stream);
		
		String mailBody = null;
		String smtp_server = env.getProperty("smtp.server");
		String smtp_port = env.getProperty("smtp.port");
		String from = env.getProperty("sender.name");
		String to = "";

		/*
		 * Call polarion web-service to get project users email list.
		 */
		PolarionConnect connect = new PolarionConnect(project_name, env);
		List<String> userlist = connect.getProjectUsers(project_name, env);

		Iterator<String> tolist = userlist.iterator();
		while(tolist.hasNext())
		{
			to = tolist.next()+", "+to;
		}
		
		String convertLoc = env.getProperty("converterLocation");
		String revListRoot=env.getProperty("revlist");
		//String pathSeparator=env.getProperty("pathSeparator");
		String projecttimezone = env.getProperty("projecttimezone");
		String svntimezone = env.getProperty("svntimezone");
		
		/*
		 * Implementing Log4J
		 */
		String log4jConfig = env.getProperty("log4j_file");
		PropertyConfigurator.configure(log4jConfig);
		
		logger.info("___________________________________________________________________________\n");
		logger.info("                     SCM COMMIT NOTIFICATION     ::    "+project_name+"    \n");
		
		
		/*
		 * Code snippet to convert date based on project time zone.
		 */
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
		String commDate = sdf.format(commitdate);
		
		LocalDateTime commitdatetime = LocalDateTime.parse(commDate, DateTimeFormatter.ofPattern(DATE_FORMAT,Locale.ENGLISH));
		ZoneId svnzoneid = ZoneId.of(svntimezone);
		ZoneId projectzone = ZoneId.of(projecttimezone);
		ZonedDateTime formatteddate = commitdatetime.atZone(svnzoneid);
		
		ZonedDateTime convertedtime = formatteddate.withZoneSameInstant(projectzone);
		DateTimeFormatter dtfmt = DateTimeFormatter.ofPattern(DATE_FORMAT);
		String zonedate = dtfmt.format(convertedtime);
		logger.info("Date :: "+zonedate+"\n");
		/*
		 * Construct diff URL.
		 */
		//Long prevRev = (Long.parseLong((String)revision) - 1);
		String projectURL = revListRoot+project_name+"/repository/browser/"+pathSeparator;
		String diffURL = "?startrevision="+revision+"&tab=revisionList";
		String adddirURL = "?crev="+revision;
		String addfileURL = "?crev="+revision+"&tab=fileContent";
		
		property.put("mail.smtp.host", smtp_server);
		property.put("mail.smtp.port", smtp_port);
		Session session = Session.getInstance(property);
		
		//String author = findUser(user, convertLoc);
		String author = connect.getUserName(user);
		logger.info("Author :: "+author+"\n");
		logger.info("Commit Message :"+commitMsg);
		
		try 
		{
			Message msg = new MimeMessage(session);
			
			msg.setFrom(new InternetAddress(from));
			msg.setRecipients(Message.RecipientType.TO,
		              InternetAddress.parse(to));
	 
			msg.setSubject("SCM COMMIT NOTIFICATION - PROJECT NAME : "+project_name);

			StringBuffer buffer = new StringBuffer();
			
			buffer.append("<style>" +
					"table{border-collapse: collapse;" +
						   "border: 1.5px solid black;" +
				 		   "table-layout: fixed;" +
				 		   "width: 100%; }" +
				 	"th{border: 1.5px solid black;" +
							"font-family: Verdana;	" +
							"font-size: 12px;" +
							"color: #333333; }" +
				 	"td{ border: 1.5px solid black;" +
				      	  "font-family: Verdana;	" +
				    	  "font-size: 12px;" +
					      "color: #333333;" +
				 	      "word-break: break-all;" +
				 	      "word-wrap: break-word; }" +
				 	 "td.setwid{ width: 12%; }" +
				 	
					"</style>" );
					
					buffer.append("<html><body>" + "<table>");
					
					buffer.append("<tr><th colspan='2' bgcolor=#999999><b>Revision "+revision+" Summary<b></th></tr>");
					
					buffer.append("<tr><td style='width: 15%;'>Author </td><td>"+author+"</td></tr>");
					buffer.append("<tr><td style='width: 15%;'>Date </td><td>"+zonedate+"</td></tr>");
					buffer.append("<tr><td style='width: 15%;'>Commit Message </td><td>");
					for(String lines: comMsgArray){
						buffer.append(lines+"<br />");
					}
					buffer.append("</td></tr>");
					buffer.append("</table>");

					buffer.append("<br><br>");
					buffer.append("<table>");
					buffer.append("<tr bgcolor=#999999><th style='width: 3%;'><b></b></th><th><b>File<b></th><th style='width: 12%;'><b>Action Performed<b></th><th style='width: 12%;'><b>Differences<b></th></tr>");
					
					for(Map.Entry<SVNLogEntryPath, String> entry : map.entrySet())
					{
						String action = entry.getValue();
						SVNLogEntryPath logPath = entry.getKey();
						String file = logPath.getPath();
						String filePathShortened = file.split(pathSeparator)[1];
						String fileURL = filePathShortened.replaceAll(" ", "%20");
						
						String kind = logPath.getKind().toString();
						String diffstr = "View";
						if((action.contentEquals("Added")) && (kind.contentEquals("dir")))
						{
							diffstr = "Not Applicable";
							buffer.append("<tr>");
							buffer.append("<td style='width: 3%;'>"+count+"</td><td>"+filePathShortened+"</td><td class='setwid'><a href="+projectURL.concat(fileURL).concat(adddirURL)+">"+action+"</a></td><td class='setwid'>"+diffstr+"</td>");
							buffer.append("</tr>");
						}
						else if((action.contentEquals("Added")) && (kind.contentEquals("file")))
						{
							diffstr = "Not Applicable";
							buffer.append("<tr>");
							buffer.append("<td style='width: 3%;'>"+count+"</td><td>"+filePathShortened+"</td><td class='setwid'><a href="+projectURL.concat(fileURL).concat(addfileURL)+">"+action+"</a></td><td class='setwid'>"+diffstr+"</td>");
							buffer.append("</tr>");
						}
						else if(action.contentEquals("Deleted"))
						{
							diffstr = "Not Applicable";
							buffer.append("<tr>");
							buffer.append("<td style='width: 3%;'>"+count+"</td><td>"+filePathShortened+"</td><td class='setwid'>"+action+"</td><td class='setwid'>"+diffstr+"</td>");
							buffer.append("</tr>");
						}
						else
						{
							buffer.append("<tr>");
							buffer.append("<td style='width: 3%;'>"+count+"</td><td>"+filePathShortened+"</td><td class='setwid'>"+action+"</td><td class='setwid'><a href="+projectURL.concat(fileURL).concat(diffURL)+">"+diffstr+"</a></td>");
							buffer.append("</tr>");
						}
						count++;
					}
					
					buffer.append("</table></html>");
			
			mailBody = buffer.toString();
		
			msg.setContent(mailBody, "text/html");
			// Send message
			Transport.send(msg);
			logger.info("Emails Sent Successfully :: "+to);
			
		} catch (AddressException e) 
			{
				e.printStackTrace();
				logger.error("AddressException "+e);
			} 
		  catch (MessagingException ex) 
			{
				ex.printStackTrace();
				logger.error("MessagingException "+ex);
			}
		  catch (Exception exx) 
		   {
			  	exx.printStackTrace();
				logger.error("Exception Caught :: "+exx);
		   }
		
	}
	
	public static SVNRepository svnAuthenticate(Properties env, String repo_path) throws SVNException
	{
		SVNRepository svnRepo = null;
		
		String gensvnURL = env.getProperty("svnURL");
		String svnUserName = env.getProperty("svnUserName");
		String svnPassWord = env.getProperty("svnPassWord");
		String svnURL = gensvnURL + repo_path;
		
		
		System.out.printf("Repo %s Using User and Pass %s   %s\n",svnURL,svnUserName,svnPassWord);
		svnRepo = SVNRepositoryFactory.create(SVNURL.parseURIDecoded(svnURL));
		ISVNAuthenticationManager svnAuthmngr = SVNWCUtil.createDefaultAuthenticationManager(svnUserName, svnPassWord);
		svnRepo.setAuthenticationManager(svnAuthmngr);
		svnRepo.testConnection();
		
		return svnRepo;
		
	}
	
	public static String findUser(String user, String convertLoc) throws IOException
	{
		Process proc = Runtime.getRuntime().exec("cmd /c "+convertLoc+" "+user);
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(proc
				.getInputStream()));
		
		String splitAuth = reader.readLine().replaceAll("\\(.*?\\) ?", "");
		
		String author = splitAuth.split("\\(")[0];
		
		return author;
	}
	
	public static void main(String[] args) throws IOException, SVNException, ServiceException {
		// TODO Auto-generated method stub
		CommonToolsMailer mailer = new CommonToolsMailer(args[0], args[1], args[2], args[3], args[4]);
		
	}

}
