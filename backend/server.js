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
const applicationId = process.env.APPLICATION_ID
const privateKey = fs.readFileSync(process.env.PRIVATE_KEY);

const credentials = {
  applicationId,
  privateKey,
};
const options = {};

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

  let ncco = [
    {
      "action": "talk",
      "text": "Please wait while we connect you to the user"
    }
  ]

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

  ncco.push ({
      "action": "connect",
      "from": from,
      "endpoint": [
        {
          "type": "app",
          "user": req.body.to
        }
      ]
  })
  res.json(ncco)
});

app.all('/voice/event', (req, res) => {
  console.log('EVENT:');
  console.dir(req.body);
  console.log('---');
  res.sendStatus(200);
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