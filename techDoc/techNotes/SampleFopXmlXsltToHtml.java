/**
* this is the sample code how FOP convert xml file to pdf or html 
* according to xslt file
*/

package boeing.ahm.servlet;

import boeing.ahm.app.User;
import boeing.ahm.data_access.AHMProperties;
import boeing.ahm.data_access.UserShadow;
import boeing.ahm.utils.MakeParmMap;
import boeing.ahm.utils.ReportWriter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/**
XSLT stuff
*/
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.Result;
import javax.xml.transform.sax.SAXResult;

/**
FOP stuff
*/
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.Fop;

/**
This class obtains the XML from utils/ReportWriter and performs the XSL 
translation to drive the requested report.
*/
public class ReportWriterServlet extends HttpServlet
{
	private static org.apache.log4j.Logger logger =
	org.apache.log4j.Logger.getLogger(ReportWriterServlet.class);

	/**
	* Initialize global variables
	*/
	public void init(ServletConfig config) throws ServletException
	{
		super.init(config);
	}

	/**
		Constructs the template name.  If custom by airline, check if file exists for
		airline, if not, use the default naming convention.
	*/
	private	String getTemplateName(String sWebBase, 
								String sReportCode, 
								String sExportType, 
								String sCustomACTmpl)
	{
		String tmp = "";

		if( sCustomACTmpl != null  && sCustomACTmpl.length() > 0)
		{
			logger.debug("sCustomACTmpl: " + sCustomACTmpl);
			tmp = sWebBase + "/xml/xslt/Report"
				+ sReportCode + "-" + sExportType + "-" + sCustomACTmpl + ".xsl";

			// check if file exists with airline code, if not, use default
			try
			{
				File tmplFile = new File(tmp);
				if( !tmplFile.exists() )
				{
					tmp = sWebBase + "/xml/xslt/Report"
						+ sReportCode + "-" + sExportType + ".xsl";
				}
			}
			catch(Exception e)
			{
				tmp = sWebBase + "/xml/xslt/Report"
					+ sReportCode + "-" + sExportType + ".xsl";
			}
		}
		else
		{
			tmp = sWebBase + "/xml/xslt/Report"
				+ sReportCode + "-" + sExportType + ".xsl";
		}

		return tmp;

	} // end of getTemplateName

	/**
	* Process the HTTP Get request.  Returns an Report's data in the requested format.
	*/
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		//
		// Static hashtable for response type
		//
		logger.info("ReportWriterServlet:Begin");
		String sErrLoc = "Begin";
		Hashtable<String,Object> responseType = new Hashtable<String,Object>();
		responseType.put("CSV","spreadsheet/comma-delimited;charset=WINDOWS-1252");
		responseType.put("XML","text/xml");
		responseType.put("HTML","text/html");
		responseType.put("PDF","application/pdf");
		//
		//  Save the parms to pass to the shadow class
		//
		MakeParmMap pmap = new MakeParmMap();
		Hashtable<String,Object> parms = pmap.getParmMap(request);
		//
		//  Add session variable(s)
		//
		UserShadow userShadow = new UserShadow(request);
		User user = userShadow.Get();
        if (parms.get("aC") == null || parms.get("aC").equals(""))
        {
            logger.debug("Set it to the user");
            parms.put("aC", user.getAirlineCode());
        }
        if (parms.get("passAC") != null && !parms.get("passAC").equals(""))
        {
            logger.debug("Pass AC =" + parms.get("passAC"));
            parms.put("aC", parms.get("passAC"));
        }

        String airlineCodes = "";
        for (Enumeration e = user.getRoles().keys(); e.hasMoreElements(); )
        {
            String key = (String) e.nextElement();
            airlineCodes += "'" + key + "'";
            if ( e.hasMoreElements() )
            {
                airlineCodes += ", ";
            }
        }
        if (!airlineCodes.equals(""))
        {
            parms.put("aClist", airlineCodes);
            parms.put("userSeq", user.getUserSeq());
        }
        
		if( user.getHasThirdParty() ) {
			parms.put("thirdPartySearch", "true");
		}
        
		logger.debug("user airlineCode is " + user.getAirlineCode());
		logger.debug("user seq is " + user.getUserSeq());
		logger.debug("user notPMType is " + user.getNotPMTypeList());
        logger.debug("param airlineCode is " + parms.get("aC"));
		parms.put("isBoeing", Integer.toString(user.getiBoeing()));
		parms.put("userSeq", Integer.toString(user.getUserSeq()));
		parms.put("notPMType", user.getNotPMTypeList());
		logger.debug("isBoeing = " + request.getParameter("isBoeing"));
                
		//
		//  Get the report code, exportType from parms
		//
		String sReportCode = request.getParameter("reportCode");
		String sExportType = request.getParameter("exportType");

		//  Check if airline has customer XSL template
		String sCustomACTmpl = request.getParameter("customACTmpl");

		//
		//  Get the output stream
		//
		OutputStream out;
		out = response.getOutputStream();
		//
		//  Get the AHM web base path
		//
		AHMProperties ahmProperties = null;
		try
		{
			ahmProperties = new AHMProperties();
		}
		catch(Exception e)
		{
			logger.error("Error reading properties: " + e.getMessage());
		}

		String sWebBase = ahmProperties.getProperty("WebBaseDir");
		String ahmRoot = ahmProperties.getProperty("AHMROOT");

		int iXMLMax = Integer.parseInt(
			ahmProperties.getProperty("ReportXMLMax"));

		try
		{
            //Figure out if this is a demo
            HttpSession session = request.getSession(true);
            User sessionuser = (User) session.getAttribute("ahm.user");
            if (sessionuser.getDemo())
            {
                parms.put("demo","true");
            }
            
			if (sReportCode == null)
			{
				logger.error("null reportCode");
				throw new Exception("report code is null");
			}
			if (sExportType == null)
			{
				logger.error("null export type");
				throw new Exception("export type is null");
			}
			logger.debug("ReportWriterServlet:XML Start");
			/*
			Use utils/ReportWriter to generate the XML
			*/
			ReportWriter rwX = new ReportWriter(parms);
			String sXML = rwX.getXML();
			//
			// Debug statements
//					logger.debug("xXML: " + sXML); //!!debug
			logger.debug("ReportWriterServlet:XML End (XML length = "
				+ Integer.toString(sXML.length()) + ")" );
			/*
			 *  Try to prevent the out of memory error for PDF.
			 *  MMMS needs to allow large amounts of output.
			 */
			if(sReportCode.length() >= 4
               && sReportCode.substring(0,4).equals("MMMS"))
			{
                String sTemp = ahmProperties.getProperty("ReportMMMSXMLMax");

                if (sTemp != null)
                {
                    iXMLMax = Integer.parseInt(sTemp);
                }
			}
            logger.debug("Report Code" + sReportCode);
            
			//if (sExportType.equals("PDF") && sXML.length() > iXMLMax)
			if (sXML.length() > iXMLMax)
			{
				sExportType = "HTML";
				sReportCode = "EM";  //ErrorMessage
				sXML = "<ReportRoot ErrorMessage=\"" 
					+ "The result set is too large to format, please "
					+ "refine your search and try again"
					+ "\"> </ReportRoot>";
			}else if (sXML.toLowerCase().contains("hasnodata")){
			
				sExportType = "HTML";
				sReportCode = "EM";  //ErrorMessage
				sXML = "<ReportRoot ErrorMessage=\"" 
					+ "No data was found matching your filter criteria."
					+ "\"> </ReportRoot>";
			}
			/*
			Got XML. Now translate it based of exportType
			Set the content type and the XSL name.
			*/
            logger.debug("Content TYpe");
            
			response.setContentType((String)responseType.get(sExportType));

			String sXSL = getTemplateName(sWebBase, sReportCode, sExportType, sCustomACTmpl);

			/*
			Do the transformation
			*/
			logger.debug("ReportWriterServlet:Transform Begin, sXSL = " + sXSL);
			logger.debug("XML test \n" + sXML);

			//
			//Create an XSLTC factory instead of the default Xalan factory.
			//
			//TransformerFactory tFactory = TransformerFactory.newInstance();
			TransformerFactory tFactory = new org.apache.xalan.xsltc.trax.TransformerFactoryImpl();
			Transformer transformer = tFactory.newTransformer(new StreamSource(sXSL));
			if(sExportType.equals("PDF")) //extra processing needed for FO
			{
										
				/*
				 * The user config file is pointing to some fonts that do not exists in the Linux environment.
				 * Uprading these FOP calls to the new api so we do not need to drag around addition old avalon code.
				 *
				String userConfig = ahmRoot + "/conf/userconfig.xml";
            	File userConfigFile = new File(userConfig);
            	Options options = new Options();
            	options.loadUserconfiguration(userConfigFile);	
				org.apache.fop.configuration.Configuration.put("baseDir",sWebBase);			
				 */
          
				FopFactory fopFactory = FopFactory.newInstance();
				Fop fop = fopFactory.newFop(org.apache.xmlgraphics.util.MimeConstants.MIME_PDF, out);
                Result res = new SAXResult(fop.getDefaultHandler());
				transformer.transform(new StreamSource(new StringReader(sXML)),res);
			}
			else
			{
				transformer.transform(new StreamSource(new StringReader(sXML)),
				new StreamResult(out));
			}
			out.flush();
			out.close();
			logger.debug("ReportWriteriServlet:Transform End");
		}  //end of try
		catch( Exception e)
		{
			logger.error("Error in ReportWriterServlet Test(in  " 
			+ sErrLoc + "): " + e.getMessage());
			logger.error("Stack Trace", e);
		}
		logger.debug("ReportWriterServlet:End");
	}

/**
 * Process the HTTP Post request - just calls doGet.
 */
	public void doPost(HttpServletRequest request, HttpServletResponse response)
		throws ServletException, IOException
	{
		doGet(request, response);
	}

/**
 * Get Servlet information
 * @return java.lang.String
 */
	public String getServletInfo()
	{
		return "ReportWriterServlet Information";
	}
}

