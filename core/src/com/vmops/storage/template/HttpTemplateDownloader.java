/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.  
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package com.vmops.storage.template;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import org.apache.commons.httpclient.ChunkedInputStream;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NoHttpResponseException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.log4j.Logger;

import com.vmops.storage.StorageLayer;
import com.vmops.utils.exception.VmopsRuntimeException;

/**
 * Download a template file using HTTP
 * @author Chiradeep
 *
 */
public class HttpTemplateDownloader implements TemplateDownloader {
	public static final Logger s_logger = Logger.getLogger(HttpTemplateDownloader.class.getName());

	private static final int CHUNK_SIZE = 1024*1024; //1M
	private String downloadUrl;
	private String toFile;
	public TemplateDownloader.Status status= TemplateDownloader.Status.NOT_STARTED;
	public String errorString = " ";
	private long remoteSize = 0;
	public long downloadTime = 0;
	public long totalBytes;
	private final HttpClient client;
	private HttpMethod request;
	private boolean resume = false;
	private DownloadCompleteCallback completionCallback;
	StorageLayer _storage;

	private String toDir;

	private final HttpMethodRetryHandler myretryhandler;

	public HttpTemplateDownloader (StorageLayer storageLayer, String downloadUrl, String toDir, DownloadCompleteCallback callback) {
		this._storage = storageLayer;
		this.downloadUrl = downloadUrl;
		this.setToDir(toDir);
		this.status = TemplateDownloader.Status.NOT_STARTED;
		
		this.totalBytes = 0;
		this.client = new HttpClient();
		myretryhandler = new HttpMethodRetryHandler() {
		    public boolean retryMethod(
		        final HttpMethod method,
		        final IOException exception,
		        int executionCount) {
		        if (executionCount >= 2) {
		            // Do not retry if over max retry count
		            return false;
		        }
		        if (exception instanceof NoHttpResponseException) {
		            // Retry if the server dropped connection on us
		            return true;
		        }
		        if (!method.isRequestSent()) {
		            // Retry if the request has not been sent fully or
		            // if it's OK to retry methods that have been sent
		            return true;
		        }
		        // otherwise do not retry
		        return false;
		    }
		};
		
		try {
			this.request = new GetMethod(downloadUrl);
			this.request.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, myretryhandler);
			this.completionCallback = callback;

			int i = downloadUrl.lastIndexOf('/');
			toFile = downloadUrl.substring(i + 1);

			File f = new File(toDir, toFile);
			
			if (_storage != null) {
				f.createNewFile();
				_storage.setWorldReadableAndWriteable(f);
			}
			
			toFile = f.getAbsolutePath();
		} catch (Exception ex){
			errorString = "Unable to start download -- check url? ";
			status = TemplateDownloader.Status.UNRECOVERABLE_ERROR;
			s_logger.warn("Exception in constructor -- " + ex.toString());
		} catch (Throwable th) {
		    s_logger.warn("throwable caught ", th);
		}
	}
	

	
	@Override
	public long download(boolean resume, DownloadCompleteCallback callback) {
        switch (status) {
        case ABORTED:
        case UNRECOVERABLE_ERROR:
        case DOWNLOAD_FINISHED:
        	return 0;
        default:

        }
        int bytes=0;
		File file = new File(toFile);
		try {
			
			long localFileSize = 0;
			if (file.exists() && resume) {
				localFileSize = file.length();
			}
			
            Date start = new Date();

			int responseCode=0;
			if (localFileSize > 0 ) {
				// require partial content support for resume
				request.addRequestHeader("Range", "bytes=" + localFileSize + "-");
				if (client.executeMethod(request) != HttpStatus.SC_PARTIAL_CONTENT) {
					errorString = "HTTP Server does not support partial get";
					status = TemplateDownloader.Status.UNRECOVERABLE_ERROR;
					return 0;
				}
			} else if ((responseCode = client.executeMethod(request)) != HttpStatus.SC_OK) {
				status = TemplateDownloader.Status.UNRECOVERABLE_ERROR;
				errorString = " HTTP Server returned " + responseCode + " (expected 200 OK) ";
                return 0; //FIXME: retry?
            }
			
			//we may have been redirected, so get the proper path and filename
			downloadUrl = request.getURI().getEscapedURI();
			String path = request.getURI().getEscapedPath();
			int i = path.lastIndexOf('/');
			toFile = path.substring(i + 1);
			file = new File(toDir, toFile);
			
			if (_storage != null) {
				file.createNewFile();
				_storage.setWorldReadableAndWriteable(file);
			}
			
			toFile = file.getAbsolutePath();
			
			
            Header contentLengthHeader = request.getResponseHeader("content-length");
            boolean chunked = false;
            if (contentLengthHeader == null) {
            	Header chunkedHeader = request.getResponseHeader("Transfer-Encoding");
            	if (chunkedHeader == null || !"chunked".equalsIgnoreCase(chunkedHeader.getValue())) {
            		status = TemplateDownloader.Status.UNRECOVERABLE_ERROR;
            		errorString=" Failed to receive length of download ";
            		return 0; //FIXME: what status do we put here? Do we retry?
            	} else if ("chunked".equalsIgnoreCase(chunkedHeader.getValue())){
            		chunked = true;
            	}
            }

            InputStream in = !chunked?new BufferedInputStream(request.getResponseBodyAsStream())
            						: new ChunkedInputStream(request.getResponseBodyAsStream());
            
            RandomAccessFile out = new RandomAccessFile(file, "rw");
            out.seek(localFileSize);
            
            remoteSize  = Long.parseLong(contentLengthHeader.getValue());
            
            byte[] block = new byte[CHUNK_SIZE];
            long offset=0;
            boolean done=false;
            status = TemplateDownloader.Status.IN_PROGRESS;
            while (!done && status != Status.ABORTED) {
            	if ( (bytes = in.read(block, 0, CHUNK_SIZE)) > -1) {
            		out.write(block, 0, bytes);
            		offset +=bytes;
            		out.seek(offset);
            		totalBytes += bytes;
            	} else {
            		done = true;
            	}
            }
            Date finish = new Date();
            String downloaded = "(incomplete download)";
            if (totalBytes >= remoteSize) {
            	status = TemplateDownloader.Status.DOWNLOAD_FINISHED;
            	downloaded = "(download complete)";
            }
            errorString = "Downloaded " + totalBytes + " bytes " + downloaded;
            downloadTime += finish.getTime() - start.getTime();
            out.close();
            
            return totalBytes;
		}catch (HttpException hte) {
			status = TemplateDownloader.Status.UNRECOVERABLE_ERROR;
			errorString = hte.getMessage();
		} catch (IOException ioe) {
			status = TemplateDownloader.Status.UNRECOVERABLE_ERROR; //probably a file write error?
			errorString = ioe.getMessage();
		} finally {
			if (status == Status.UNRECOVERABLE_ERROR && file.exists() && !file.isDirectory()) {
				file.delete();
			}
			request.releaseConnection();
            if (callback != null) {
            	callback.downloadComplete(status);
            }
		}
		return 0;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}

	public String getToFile() {
        File file = new File(toFile);

		return file.getAbsolutePath();
	}

	public TemplateDownloader.Status getStatus() {
		return status;
	}


	public long getDownloadTime() {
		return downloadTime;
	}
	
	
	public long getDownloadedBytes() {
		return totalBytes;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public boolean stopDownload() {
		switch (getStatus()) {
		case IN_PROGRESS:
			if (request != null) {
				request.abort();
			}
			status = TemplateDownloader.Status.ABORTED;
			return true;
		case UNKNOWN:
		case NOT_STARTED:
		case RECOVERABLE_ERROR:
		case UNRECOVERABLE_ERROR:
		case ABORTED:
			status = TemplateDownloader.Status.ABORTED;
		case DOWNLOAD_FINISHED:
			File f = new File(toFile);
			if (f.exists()) {
				f.delete();
			}
			return true;

		default:
			return true;
		}
	}

	@Override
	public int getDownloadPercent() {
		if (remoteSize == 0) {
			return 0;
		}
		
		return (int)(100.0*totalBytes/remoteSize);
	}

	@Override
	public void run() {
		try {
			download(resume, completionCallback);
		} catch (Throwable t) {
			s_logger.warn("Caught exception during download "+ t.getMessage(), t);
			errorString = "Failed to install: " + t.getMessage();
			status = TemplateDownloader.Status.UNRECOVERABLE_ERROR;
		}
		
	}

	@Override
	public void setStatus(TemplateDownloader.Status status) {
		this.status = status;
	}



	public boolean isResume() {
		return resume;
	}

	@Override
	public String getDownloadError() {
		return errorString;
	}

	@Override
	public String getDownloadLocalPath() {
		return getToFile();
	}

	public void setResume(boolean resume) {
		this.resume = resume;
	}

	public void setToDir(String toDir) {
		this.toDir = toDir;
	}

	public String getToDir() {
		return toDir;
	}


	
	public static void main(String[] args) {
		String url ="http:// dev.mysql.com/get/Downloads/MySQL-5.0/mysql-noinstall-5.0.77-win32.zip/from/http://mirror.services.wisc.edu/mysql/";
		try {
			URI uri = new java.net.URI(url);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		TemplateDownloader td = new HttpTemplateDownloader(null, url,"/tmp/mysql", null );
		long bytes = td.download(true, null);
		if (bytes > 0) {
			System.out.println("Downloaded  (" + bytes + " bytes)" + " in " + td.getDownloadTime()/1000 + " secs");
		} else {
			System.out.println("Failed download");
		}

	}

	@Override
	public void setDownloadError(String error) {
		errorString = error;
	}

}
