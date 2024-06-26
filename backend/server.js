import {Vonage} from '@vonage/server-sdk';
import { tokenGenerate } from '@vonage/jwt';
import express from 'express';
import * as dotenv from 'dotenv';
import axios from 'axios';
import cors from 'cors';
import fs from 'fs';
import path from 'path';

dotenv.config()

const port = process.env.PORT || 3003; 
const app = express();
const __dirname = path.resolve()
app.use(express.json());
app.use(cors());
app.use(express.static(path.join(__dirname, 'public')));


const restAPI = 'https://api-ap-3.vonage.com/v0.3'
const applicationId = process.env.VONAGE_APPLICATION_ID
const privateKey = fs.readFileSync(process.env.VONAGE_PRIVATE_KEY_PATH);

const credentials = {
  applicationId,
  privateKey,
};
const options = {};

const vonage = new Vonage(credentials, options);

const failStatus = ["timeout"];

const aclPaths = {
    "paths": {
      "/*/users/**": {},
      "/*/conversations/**": {},
      "/*/sessions/**": {},
      "/*/devices/**": {},
      "/*/image/**": {},
      "/*/media/**": {},
      "/*/applications/**": {},
      "/*/push/**": {},
      "/*/knocking/**": {},
      "/*/legs/**": {}
    }
}


app.get('/contact-center', async(req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.post('/getCredential', async (req, res) => {
  // use phone number as username
  const {displayName, phoneNumber} = req.body;
  if (!displayName || !phoneNumber) {
    console.log("getCredential missing information error")
    return res.status(501).end()
  }

  axios.get(`${restAPI}/users?name=${phoneNumber}`, { headers: {"Authorization" : `Bearer ${generateJwt()}`} })
  .then(async (result) => {
      console.log("user exist", result.data._embedded.users[0].name)
      const jwt = generateJwt(phoneNumber) 
      return res.status(200).json({ displayName: displayName, username: phoneNumber, userId: result.data._embedded.users[0].id, token: jwt});
  })
  .catch(error => {
    // Create user
    axios.post(`${restAPI}/users`, {
      "name":  phoneNumber,
      "display_name": displayName
    } , { headers: {"Authorization" : `Bearer ${generateJwt()}`} })
    .then(async (result) => {
      console.log("user not exist",result.data.name)
      const jwt = generateJwt(phoneNumber)

      return res.status(200).json({displayName: displayName, username: phoneNumber, userId: result.data.id, token: jwt});
    }).catch(error => {
      console.log("register error", error)
        res.status(501).send()
    })      
  })
});

app.delete('/deleteUser', async (req, res) => {
    const {userId} = req.body;
    if (!userId) {
        console.log("deleteUser missing information error")
        return res.status(501).end()
    }

    try {
        await deleteUser(userId)
        return res.status(200).end()
    } catch (error) {
        console.log("deleteuser error:", error)
        return res.status(501).end()
    }
})

app.post('/voice/answer', (req, res) => {
  console.log('NCCO request:');
  console.log(`  - callee: ${req.body}`);
  console.log('---');

  // Call from mobile app user to contact center
  if (isNaN(req.body.to)) {
    let to = ""
    switch(req.body.to) {
      case "Refund Request":
        to = "12013744445"
        break;
      case "Zendesk Report":
        to = "12013744446"
        break;
        case "API Query":
          to = "12013744447"
          break;
      default:
        to = "12013744448"
    }

    // Call to contact center webapp
    let ncco = [
      {
        "action": "talk",
        "text": "Please wait while we connect you to an agent"
      },
      {
        "action": "connect",
        "from": req.body.from,
        "endpoint": [
          {
          "type": "app",
          "user": to
        }
       ]
      }
    ]

    // Call to contact center sip
    /*
    let ncco = [
      {
        "action": "talk",
        "text": "Please wait while we connect you to an agent"
      },
      {
        "action": "connect",
        "from": req.body.from,
        "endpoint": [
          {
            "type": "sip",
            "uri": process.env.CC_SIP, // connect to sip
            "headers": { "User-to-User": req.body.to }  // passing user information to sip header
          }
        ]
      }
    ]
    */
    return res.json(ncco)
  }

  let ncco = [
    {
      "action": "talk",
      "text": "Please wait while we connect you to the user"
    }
  ]

  axios.get(`${restAPI}/users?name=${req.body.to}`, { headers: {"Authorization" : `Bearer ${generateJwt()}`} })
  .then(async (result) => {
    console.log("user exist", result.data._embedded.users[0].name)
      // Intent mapping
      let from = ""
      let switchCase = req.body.from ?? req.body.from_user
      switch(switchCase) {
        case "12013744445":
          from = "Vonage API Refund Request #3788"
          break;
        case "12013744446":
          from = "Vonage Zendesk Report #9125"
          break;
          case "12013744447":
            from = "Vonage API Query Case #367 "
            break;
        default:
          from = "Vonage Customer Support"
      }

      // If user exist, call to user's mobile app
      ncco.push ({
          "action": "connect",
          "from": from,
          "timeout" : 25,
          "eventType": "synchronous",
          "endpoint": [
            {
              "type": "app",
              "user": req.body.to
            }
          ]
      })
      res.json(ncco)
  })
  .catch(error => {
    // If user not exist, call to user's PSTN number
    ncco.push ({
      "action": "connect",
      "from": process.env.VONAGE_PHONE_NUMBER,
      "endpoint": [
        {
          "type": "phone",
          "number": req.body.to
        }
      ]
    })

      res.json(ncco)
  })
});

app.all('/voice/event', (req, res) => {
  console.log('EVENT:');
  console.dir(req.body);
  console.log('---');

  // If mobile app user no pick up, fallback to PSTN call
  if (failStatus.includes(req.body.status)) {
    vonage.voice.getCall(req.body.uuid)
    .then(async resp => {
      console.log("event getcall response ", resp)

      if (resp.to.type == "app") {
        // Fallback to phone call
        const ncco = [
          {
            "action": "talk",
            "text": "Fallback to pstn call"
          },
          {
          "action": "connect",
          "from": process.env.VONAGE_PHONE_NUMBER,
          "endpoint": [
            {
              "type": "phone",
              "number": req.body.to
            }
          ]
        }
        ]
        res.json(ncco)
      }
      else {
        res.sendStatus(200);
      }
    })
    .catch(err => {
      console.error(err)
      res.sendStatus(200);
    });
  } else {
    res.sendStatus(200);
  }
});

function deleteUser(userId) {
    return new Promise((resolve, reject) => {
      axios.delete(`${restAPI}/users/${userId}`, { headers: {"Authorization" : `Bearer ${generateJwt()}`} })
      .then(async (result) => {
          console.log("user deleted")
          resolve()
      })
      .catch(error => {
          console.log("delete user error: ", error)
          reject(error)
      })
    })
}

function generateJwt(username) {
    if (!username) {
      const adminJwt = tokenGenerate(applicationId, privateKey, {
        exp: Math.round(new Date().getTime()/1000)+86400,
        acl: aclPaths
      });
      return adminJwt
    }
    
    const jwt = tokenGenerate(applicationId, privateKey, {
      sub: username,
      exp: Math.round(new Date().getTime()/1000)+86400,
      acl: aclPaths
      });
  
    return jwt
}

app.listen(port, () => console.log(`Listening on port ${port}`)); 