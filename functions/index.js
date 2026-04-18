/**
 * Firebase Cloud Functions for AI Guardian
 * 
 * To deploy:
 * 1. Install firebase-tools: npm install -g firebase-tools
 * 2. Login: firebase login
 * 3. Init functions: firebase init functions (choose Javascript)
 * 4. Replace index.js with this content
 * 5. Deploy: firebase deploy --only functions
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// Listen for SOS events
exports.onSosTriggered = functions.database.ref('/events/sos/{pushId}')
    .onCreate(async (snapshot, context) => {
        const event = snapshot.val();
        const elderPin = event.elderPin;
        const locationUrl = event.location;

        // Find caretakers for this elder
        const userSnapshot = await admin.database().ref(`/users/${elderPin}/caretakers`).once('value');
        const caretakers = userSnapshot.val();

        if (!caretakers) return null;

        const tokens = Object.values(caretakers);
        const payload = {
            data: {
                type: 'SOS',
                title: '🚨 EMERGENCY SOS!',
                body: `Your loved one (PIN ${elderPin}) needs help!`,
                locationUrl: locationUrl
            }
        };

        return admin.messaging().sendToDevice(tokens, payload);
    });

// Listen for Scam detections
exports.onScamDetected = functions.database.ref('/events/scams/{pushId}')
    .onCreate(async (snapshot, context) => {
        const event = snapshot.val();
        const elderPin = event.elderPin;
        const phone = event.phoneNumber;
        const locationUrl = event.location;

        // Find caretakers
        const userSnapshot = await admin.database().ref(`/users/${elderPin}/caretakers`).once('value');
        const caretakers = userSnapshot.val();

        if (!caretakers) return null;

        const tokens = Object.values(caretakers);
        const payload = {
            data: {
                type: 'SCAM',
                title: '🛡️ SCAM BLOCKED',
                body: `We blocked a suspicious call from ${phone} for PIN ${elderPin}.`,
                locationUrl: locationUrl
            }
        };

        return admin.messaging().sendToDevice(tokens, payload);
    });
