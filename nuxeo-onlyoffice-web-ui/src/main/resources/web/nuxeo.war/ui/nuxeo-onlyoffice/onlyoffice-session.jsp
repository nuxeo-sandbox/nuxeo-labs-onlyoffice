<!--
@license
(C) Copyright Nuxeo Corp. (http://nuxeo.com/)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

<%@page import="org.nuxeo.runtime.api.Framework"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<!DOCTYPE html>
<html>

<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>ONLYOFFICE</title>
  <style>
    html {
      height: 100%;
      width: 100%;
    }

    body {
      background: #fff;
      color: #333;
      font-family: Arial, Tahoma, sans-serif;
      font-size: 12px;
      font-weight: normal;
      height: 100%;
      margin: 0;
      overflow-y: hidden;
      padding: 0;
      text-decoration: none;
    }

    .form {
      height: 100%;
    }

    div {
      margin: 0;
      padding: 0;
    }
</style>

  <%
    // Prefer `nuxeo.url` (fully qualified, e.g. `https://host/nuxeo`) so callback
    // and blob-download URLs sent to ONLYOFFICE Document Server match what the
    // integrator configured. Fall back to the servlet context path prefixed by
    // the browser's `location.origin` at runtime. Either way, both values must
    // be reachable from the Document Server container (Docker gotcha).
    String api = Framework.getProperty("onlyoffice.url.api");
    String nuxeoBase = Framework.getProperty("nuxeo.url");
    if (nuxeoBase != null) {
      nuxeoBase = nuxeoBase.trim();
      if (nuxeoBase.endsWith("/")) {
        nuxeoBase = nuxeoBase.substring(0, nuxeoBase.length() - 1);
      }
    }
    String contextPath = request.getContextPath();
  %>
  <script type="text/javascript" src="<%= api %>"></script>
  <script type="text/javascript">
    // Base URL for building callback and blob-download URLs. Prefer the
    // server-configured `nuxeo.url`; otherwise reconstruct from `location.origin`
    // + the servlet context path.
    var nuxeoBase = <%= (nuxeoBase != null) ? "\"" + nuxeoBase + "\"" : "location.origin + \"" + contextPath + "\"" %>;
    var docEditor;

    var innerAlert = function (message) {
      if (console && console.log) {
        console.log(message);
      }
    };

    var onDocumentStateChange = function (event) {
      var title = document.title.replace(/\*$/g, "");
      document.title = title + (event.data ? "*" : "");
    };

    var onError = function (event) {
      if (event) {
        innerAlert(event.data);
      }
    };

    var onOutdatedVersion = function (event) {
      location.reload(true);
    };

    var mobileDevice = function () {
      var check = false;
      (function (a) {
        if (
          /(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino/i
          .test(a) ||
          /1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i
          .test(a.substr(0, 4))) check = true;
      })(navigator.userAgent || navigator.vendor || window.opera);
      return check;
    };

    var сonnectEditor = function () {
      var urlParams = new URLSearchParams(window.location.search);

      var uid = urlParams.get("id");
      var token = urlParams.get("token");
      var key = urlParams.get("key");
      var xpath = urlParams.get("xpath") || 'file:content';
      var fname = urlParams.get("fname") || 'document.docx';
      var docType = urlParams.get("type") || 'text';
      var mode = urlParams.get("mode") || 'desktop';
      var userId = urlParams.get("user");

      var fileType = fname.substring(fname.lastIndexOf(".") + 1).toLowerCase();
      var writable = false;

      if (mode !== 'embedded') {
        writable = true;
        if (mobileDevice()) {
          mode = 'mobile';
        }
      }

      if (!(uid && token)) {
        return;
      }

      console.log("uid:" + uid + ", mode:" + mode + ", key:" + key + ", xpath: " + xpath +
        ", name:" + fname + " (" + fileType + "), type:" + docType);

      var share = nuxeoBase + '/ui/#!/doc/' + uid;
      var blob = nuxeoBase + '/nxfile/default/' + uid +
        '/' + xpath + '/' + fname + '?inline=true&token=' + token;
      var callback = nuxeoBase + '/api/v1/onlyoffice/callback/' +
        uid + '/' + xpath + '?token=' + token;

      var config = {
        "documentType": docType,
        "editorConfig": {
          "callbackUrl": callback,
          "customization": {
            "autosave": false,
            "compactToolbar": false,
            "forcesave": false,
            "showReviewChanges": false,
            "zoom": 100
          },
          "embedded": {
            "saveUrl": blob,
            "shareUrl": share,
            "toolbarDocked": "top"
          },
          "lang": "en-US",
          "mode": "edit"
        },
        "height": "100%",
        "type": mode,
        "width": "100%",
        "events": {
          "onDocumentStateChange": onDocumentStateChange,
          "onOutdatedVersion": onOutdatedVersion,
          "onError": onError,
        }
      };

      if (key) {
        config.document = {
          "fileType": fileType,
          "key": key,
          "permissions": {
            "chat": writable,
            "comment": writable,
            "edit": writable,
            "review": writable,
            "print": true,
            "download": true
          },
          "title": fname,
          "url": blob
        }
      }

      if (userId) {
        config.editorConfig.user = {
          "id": userId,
          "name": userId
        };
      }

      docEditor = new DocsAPI.DocEditor("iframeEditor", config);
    };

    if (window.addEventListener) {
      window.addEventListener("load", сonnectEditor);
    } else if (window.attachEvent) {
      window.attachEvent("load", сonnectEditor);
    }

  </script>

</head>

<body>
  <div class="form">
    <div id="iframeEditor"></div>
  </div>
</body>

</html>
