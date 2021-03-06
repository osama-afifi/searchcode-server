/*
 * Copyright (c) 2016 Boyter Online Services
 *
 * Use of this software is governed by the Fair Source License included
 * in the LICENSE.TXT file, but will be eventually open under GNU General Public License Version 3
 * see the README.md for when this clause will take effect
 *
 * Version 1.3.8
 */

package com.searchcode.app.service.route;

import com.searchcode.app.config.Values;
import com.searchcode.app.dao.IRepo;
import com.searchcode.app.dao.Repo;
import com.searchcode.app.dto.api.ApiResponse;
import com.searchcode.app.dto.api.RepoResultApiResponse;
import com.searchcode.app.model.RepoResult;
import com.searchcode.app.service.ApiService;
import com.searchcode.app.service.IApiService;
import com.searchcode.app.service.IJobService;
import com.searchcode.app.service.Singleton;
import com.searchcode.app.util.Properties;
import com.searchcode.app.util.UniqueRepoQueue;
import spark.Request;
import spark.Response;

import java.net.URLEncoder;
import java.util.List;

public class ApiRouteService {

    private final IApiService apiService;
    private final IJobService jobService;
    private final IRepo repo;
    private final UniqueRepoQueue uniqueDeleteQueue;

    public boolean apiEnabled = Boolean.parseBoolean(Properties.getProperties().getProperty("api_enabled", "false"));
    public boolean apiAuth = Boolean.parseBoolean(Properties.getProperties().getProperty("api_key_authentication", "true"));

    public ApiRouteService() {
        this.apiService = Singleton.getApiService();
        this.jobService = Singleton.getJobService();
        this.repo = Singleton.getRepo();
        this.uniqueDeleteQueue = Singleton.getUniqueDeleteRepoQueue();
    }

    public ApiRouteService(IApiService apiService, IJobService jobService, IRepo repo, UniqueRepoQueue uniqueDeleteQueue){
        this.apiService = apiService;
        this.jobService = jobService;
        this.repo = repo;
        this.uniqueDeleteQueue = uniqueDeleteQueue;
    }

    public ApiResponse repositoryReindex(Request request, Response response) {
        if (!this.apiEnabled) {
            return new ApiResponse(false, "API not enabled");
        }

        String publicKey = request.queryParams("pub");
        String signedKey = request.queryParams("sig");
        String hmacTypeString = request.queryParams("hmac");

        hmacTypeString = hmacTypeString == null ? Values.EMPTYSTRING : hmacTypeString;

        if (apiAuth) {
            if (publicKey == null || publicKey.trim().equals(Values.EMPTYSTRING)) {
                return new ApiResponse(false, "pub is a required parameter");
            }

            if (signedKey == null || signedKey.trim().equals(Values.EMPTYSTRING)) {
                return new ApiResponse(false, "sig is a required parameter");
            }

            String toValidate = String.format("pub=%s", URLEncoder.encode(publicKey));

            ApiService.HmacType hmacType = hmacTypeString.toLowerCase().equals("sha512") ? ApiService.HmacType.SHA512 : ApiService.HmacType.SHA1;
            boolean validRequest = apiService.validateRequest(publicKey, signedKey, toValidate, hmacType);

            if (!validRequest) {
                return new ApiResponse(false, "invalid signed url");
            }
        }

        boolean result = this.jobService.rebuildAll();
        if (result) {
            this.jobService.forceEnqueue();
            return new ApiResponse(true, "reindex forced");
        }

        return new ApiResponse(false, "was unable to force the index");
    }

    public ApiResponse repositoryIndex(Request request, Response response) {
        if (!this.apiEnabled) {
            return new ApiResponse(false, "API not enabled");
        }

        String repoUrl = request.queryParams("repoUrl");
        RepoResult repoByUrl = this.repo.getRepoByUrl(repoUrl);

        if (repoByUrl != null) {
            this.jobService.forceEnqueue(repoByUrl);
            return new ApiResponse(true, "Enqueued repository " + repoUrl);
        }

        return new ApiResponse(false, "Was unable to find repository " + repoUrl);
    }

    public RepoResultApiResponse repoList(Request request, Response response) {
        if (!this.apiEnabled) {
            return new RepoResultApiResponse(false, "API not enabled", null);
        }

        String publicKey = request.queryParams("pub");
        String signedKey = request.queryParams("sig");
        String hmacTypeString = request.queryParams("hmac");
        hmacTypeString = hmacTypeString == null ? Values.EMPTYSTRING : hmacTypeString;

        if (apiAuth) {
            if (publicKey == null || publicKey.trim().equals(Values.EMPTYSTRING)) {
                return new RepoResultApiResponse(false, "pub is a required parameter", null);
            }

            if (signedKey == null || signedKey.trim().equals(Values.EMPTYSTRING)) {
                return new RepoResultApiResponse(false, "sig is a required parameter", null);
            }

            String toValidate = String.format("pub=%s",
                    URLEncoder.encode(publicKey));

            ApiService.HmacType hmacType = hmacTypeString.toLowerCase().equals("sha512") ? ApiService.HmacType.SHA512 : ApiService.HmacType.SHA1;
            boolean validRequest = apiService.validateRequest(publicKey, signedKey, toValidate, hmacType);

            if (!validRequest) {
                return new RepoResultApiResponse(false, "invalid signed url", null);
            }
        }

        List<RepoResult> repoResultList = repo.getAllRepo();

        return new RepoResultApiResponse(true, Values.EMPTYSTRING, repoResultList);

    }

    public ApiResponse repoDelete(Request request, Response response) {
        if (!apiEnabled) {
            return new ApiResponse(false, "API not enabled");
        }

        String publicKey = request.queryParams("pub");
        String signedKey = request.queryParams("sig");
        String reponames = request.queryParams("reponame");
        String hmacTypeString = request.queryParams("hmac");
        hmacTypeString = hmacTypeString == null ? Values.EMPTYSTRING : hmacTypeString;

        if (reponames == null || reponames.trim().equals(Values.EMPTYSTRING)) {
            return new ApiResponse(false, "reponame is a required parameter");
        }

        if (apiAuth) {
            if (publicKey == null || publicKey.trim().equals(Values.EMPTYSTRING)) {
                return new ApiResponse(false, "pub is a required parameter");
            }

            if (signedKey == null || signedKey.trim().equals(Values.EMPTYSTRING)) {
                return new ApiResponse(false, "sig is a required parameter");
            }

            String toValidate = String.format("pub=%s&reponame=%s",
                    URLEncoder.encode(publicKey),
                    URLEncoder.encode(reponames));

            ApiService.HmacType hmacType = hmacTypeString.toLowerCase().equals("sha512") ? ApiService.HmacType.SHA512 : ApiService.HmacType.SHA1;
            boolean validRequest = apiService.validateRequest(publicKey, signedKey, toValidate, hmacType);

            if (!validRequest) {
                return new ApiResponse(false, "invalid signed url");
            }
        }

        RepoResult rr = this.repo.getRepoByName(reponames);
        if (rr == null) {
            return new ApiResponse(false, "repository already deleted");
        }

        this.uniqueDeleteQueue.add(rr);

        return new ApiResponse(true, "repository queued for deletion");
    }

    public ApiResponse repoAdd(Request request, Response response) {
        if (!this.apiEnabled) {
            return new ApiResponse(false, "API not enabled");
        }

        String publicKey = request.queryParams("pub");
        String signedKey = request.queryParams("sig");
        String reponames = request.queryParams("reponame");
        String repourls = request.queryParams("repourl");
        String repotype = request.queryParams("repotype");
        String repousername = request.queryParams("repousername");
        String repopassword = request.queryParams("repopassword");
        String reposource = request.queryParams("reposource");
        String repobranch = request.queryParams("repobranch");
        String hmacTypeString = request.queryParams("hmac");
        hmacTypeString = hmacTypeString == null ? Values.EMPTYSTRING : hmacTypeString;

        if (reponames == null || reponames.trim().equals(Values.EMPTYSTRING)) {
            return new ApiResponse(false, "reponame is a required parameter");
        }

        if (repourls == null || repourls.trim().equals(Values.EMPTYSTRING)) {
            return new ApiResponse(false, "repourl is a required parameter");
        }

        if (repotype == null) {
            return new ApiResponse(false, "repotype is a required parameter");
        }

        if (repousername == null) {
            return new ApiResponse(false, "repousername is a required parameter");
        }

        if (repopassword == null) {
            return new ApiResponse(false, "repopassword is a required parameter");
        }

        if (reposource == null) {
            return new ApiResponse(false, "reposource is a required parameter");
        }

        if (repobranch == null) {
            return new ApiResponse(false, "repobranch is a required parameter");
        }

        if (apiAuth) {
            if (publicKey == null || publicKey.trim().equals(Values.EMPTYSTRING)) {
                return new ApiResponse(false, "pub is a required parameter");
            }

            if (signedKey == null || signedKey.trim().equals(Values.EMPTYSTRING)) {
                return new ApiResponse(false, "sig is a required parameter");
            }

            String toValidate = String.format("pub=%s&reponame=%s&repourl=%s&repotype=%s&repousername=%s&repopassword=%s&reposource=%s&repobranch=%s",
                    URLEncoder.encode(publicKey),
                    URLEncoder.encode(reponames),
                    URLEncoder.encode(repourls),
                    URLEncoder.encode(repotype),
                    URLEncoder.encode(repousername),
                    URLEncoder.encode(repopassword),
                    URLEncoder.encode(reposource),
                    URLEncoder.encode(repobranch));

            ApiService.HmacType hmacType = hmacTypeString.toLowerCase().equals("sha512") ? ApiService.HmacType.SHA512 : ApiService.HmacType.SHA1;
            boolean validRequest = apiService.validateRequest(publicKey, signedKey, toValidate, hmacType);

            if (!validRequest) {
                return new ApiResponse(false, "invalid signed url");
            }
        }


        // Clean
        if (repobranch == null || repobranch.trim().equals(Values.EMPTYSTRING)) {
            repobranch = "master";
        }

        repotype = repotype.trim().toLowerCase();
        if (!"git".equals(repotype) && !"svn".equals(repotype) && !"file".equals(repotype)) {
            repotype = "git";
        }

        RepoResult repoResult = this.repo.getRepoByName(reponames);

        if (repoResult != null) {
            return new ApiResponse(false, "repository name already exists");
        }

        this.repo.saveRepo(new RepoResult(-1, reponames, repotype, repourls, repousername, repopassword, reposource, repobranch, "{}"));

        return new ApiResponse(true, "added repository successfully");
    }
}
