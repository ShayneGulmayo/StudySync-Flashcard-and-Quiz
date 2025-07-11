// Import necessary modules from firebase-admin and firebase-functions/v2
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { logger } from 'firebase-functions';
import { getMessaging } from 'firebase-admin/messaging';

// Import external libraries for file parsing
import pdfParse from 'pdf-parse';
import mammoth from 'mammoth';
import pptx2json from 'pptx2json';
// Import Vertex AI specific client library
import { VertexAI, HarmCategory, HarmBlockThreshold } from '@google-cloud/vertexai';

// Initialize Firebase Admin SDK
initializeApp();
const db = getFirestore();

// Initialize Vertex AI
// Ensure your Cloud Function's region matches the Vertex AI region.
// For example, 'us-central1' is a common region for Vertex AI.
const project = process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT; // Get project ID from environment
const location = 'us-central1'; // Or your desired Vertex AI region (e.g., 'asia-southeast1')
const vertexAI = new VertexAI({ project: project, location: location });


const model = 'gemini-2.5-flash'; // Using gemini-2.5-flash as requested
const generativeModel = vertexAI.getGenerativeModel({
    model: model,
    // Configure generation parameters for structured JSON output
    generationConfig: {
        maxOutputTokens: 2048,
        temperature: 0.7,
        topP: 0.95,
        topK: 40,
        responseMimeType: "application/json", // Request JSON output from Gemini
        responseSchema: { // Define the expected JSON schema for Gemini's response
            type: "OBJECT",
            properties: {
                "title": { "type": "STRING" },
                "terms": {
                    "type": "ARRAY",
                    "items": {
                        "type": "OBJECT",
                        "properties": {
                            "term": { "type": "STRING" },
                            "definition": { "type": "STRING" }
                        },
                        "required": ["term", "definition"]
                    }
                }
            },
            "required": ["title", "terms"]
        }
    },
    safetySettings: [
        {
            category: HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT,
            threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE,
        },
        {
            category: HarmCategory.HARM_CATEGORY_HARASSMENT,
            threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE,
        },
        {
            category: HarmCategory.HARM_CATEGORY_HATE_SPEECH,
            threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE,
        },
        {
            category: HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT,
            threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE,
        },
    ],
});


/**
 * Extracts text content from a given file buffer based on its MIME type.
 * @param {Buffer} fileBuffer - The buffer of the file.
 * @param {string} mimeType - The MIME type of the file (e.g., 'application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document').
 * @returns {Promise<string>} - A promise that resolves with the extracted text.
 */
async function extractTextFromFile(fileBuffer, mimeType) {
    logger.info(`Attempting to extract text from file with MIME type: ${mimeType}`);
    switch (mimeType) {
        case 'application/pdf':
            const pdfData = await pdfParse(fileBuffer);
            return pdfData.text;
        case 'application/vnd.openxmlformats-officedocument.wordprocessingml.document': // .docx
            const docxResult = await mammoth.extractRawText({ buffer: fileBuffer });
            return docxResult.value; // The raw text
        case 'application/vnd.openxmlformats-officedocument.presentationml.presentation': // .pptx
            // pptx2json typically returns an array of slide objects.
            // We need to iterate through slides and their shapes to get all text.
            const slides = await new Promise((resolve, reject) => {
                pptx2json(fileBuffer, (err, result) => {
                    if (err) reject(err);
                    else resolve(result);
                });
            });

            let pptxText = '';
            if (slides && Array.isArray(slides)) {
                slides.forEach(slide => {
                    if (slide.shapes && Array.isArray(slide.shapes)) {
                        slide.shapes.forEach(shape => {
                            if (shape.text) { // Ensure shape.text exists
                                pptxText += shape.text + '\n';
                            }
                        });
                    }
                });
            }
            return pptxText;
        default:
            throw new Error(`Unsupported file type: ${mimeType}`);
    }
}

/**
 * HttpsCallable function to generate flashcards from a file.
 *
 * @param {object} request - The request object from the client.
 * @param {object} request.data - The data payload.
 * @param {string} request.data.base64File - Base64 encoded string of the file.
 * @param {string} request.data.mimeType - The MIME type of the file.
 * @param {string} request.data.uid - The UID of the user who initiated the request.
 * @returns {object} - A response object indicating success or failure.
 */
export const generateFlashcards = onCall(async (request) => {
  try {
    // Authenticate the request
    if (!request.auth) {
      throw new HttpsError('unauthenticated', 'The function must be called while authenticated.');
    }

    const { base64File, mimeType, uid } = request.data;

        if (!base64File || !mimeType || !uid) {
          logger.error("Missing required fields in request data.", {
            base64File: !!base64File,
            mimeType: !!mimeType,
            uid: !!uid,
          });
          throw new HttpsError(
            "invalid-argument",
            "Missing required fields: base64File, mimeType, or uid."
          );
        }

        let extractedText = "";
        try {
          const fileBuffer = Buffer.from(base64File, "base64");
          extractedText = await extractTextFromFile(fileBuffer, mimeType);
          logger.info(
            "File text extracted successfully. Length:",
            extractedText.length
          );
        } catch (error) {
          logger.error("Error extracting text from file:", error);
          throw new HttpsError(
            "internal",
            "Failed to extract text from file. Please ensure it is a valid PDF, DOCX, or PPTX.",
            error.message
          );
        }

        if (extractedText.trim().length === 0) {
          logger.warn("Extracted text content is empty.");
          throw new HttpsError(
            "failed-precondition",
            "Extracted text from file is empty. Cannot generate flashcards. The file might be empty or contain only images."
          );
        }

        let flashcardData = {};
        try {
          const prompt = `Analyze the provided document text and generate a set of flashcards...`;

          logger.info("Sending prompt and content to Gemini AI for flashcard generation.");

          const result = await generativeModel.generateContent({
            contents: [{ role: "user", parts: [{ text: prompt }, { text: extractedText }] }],
          });

          const response = result.response;
          const jsonText = response.candidates[0].content.parts[0].text;
          logger.info("Gemini AI raw response:", jsonText);

          flashcardData = JSON.parse(jsonText);

          if (
            !flashcardData.title ||
            !flashcardData.terms ||
            !Array.isArray(flashcardData.terms)
          ) {
            logger.error(
              "Gemini response missing required fields or invalid format.",
              flashcardData
            );
            throw new HttpsError(
              "internal",
              "AI response was not in the expected format. Please try again or with a different file."
            );
          }

          logger.info(`Generated ${flashcardData.terms.length} flashcards from Gemini AI.`);
        } catch (error) {
          logger.error("Error calling Gemini AI or parsing response:", error);
          if (error instanceof HttpsError) {
            throw error;
          } else {
            throw new HttpsError(
              "internal",
              "Failed to generate flashcards with AI. The AI might have returned an invalid format or an error occurred.",
              error.message
            );
          }
        }

        const accessUsers = { [uid]: "Owner" };
        const termsMap = {};
        flashcardData.terms.forEach((item, index) => {
          termsMap[index.toString()] = item;
        });

        const doc = {
          title: flashcardData.title,
          owner_uid: uid,
          createdAt: FieldValue.serverTimestamp(),
          privacy: "private",
          privacyRole: "edit",
          number_of_items: flashcardData.terms.length,
          accessUsers: accessUsers,
          terms: termsMap,
        };

        try {
          const docRef = await db.collection("flashcards").add(doc);
          logger.info(`Flashcard set saved to Firestore with ID: ${docRef.id}`);
          return {
            success: true,
            title: flashcardData.title,
            flashcardId: docRef.id,
          };
        } catch (error) {
          logger.error("Error saving flashcards to Firestore:", error);
          throw new HttpsError("internal", "Failed to save flashcards to Firestore.", error.message);
        }

      } catch (error) {
        // ✅ This catch block fixes the syntax error
        logger.error("generateFlashcards failed at top level:", error);
        if (error instanceof HttpsError) {
          throw error;
        } else {
          throw new HttpsError("internal", "Unexpected error occurred.", error.message);
        }
      }
    });


// --- Chat Room Message Notification (existing function, included as is) ---

export const sendChatRoomMessageNotification = onDocumentCreated(
  "chat_rooms/{chatRoomId}/messages/{messageId}", async (event) => {
    const snapshot = event.data;
    const chatRoomId = event.params.chatRoomId;

    if (!snapshot) {
      logger.warn("No message data found for notification.");
      return;
    }

    const message = snapshot.data();

    const chatRoomDoc = await db.collection("chat_rooms").doc(chatRoomId).get();
    if (!chatRoomDoc.exists) {
      logger.warn(`Chat room ${chatRoomId} not found for notification.`);
      return;
    }

    const chatRoom = chatRoomDoc.data();
    const members = chatRoom.members || [];
    const chatRoomName = chatRoom.chatRoomName || "New Message";

    let senderFirstName = "Someone";
    if (message.senderId && message.senderId !== "system") {
      const senderDoc = await db.collection("users").doc(message.senderId).get();
      if (senderDoc.exists) {
        const senderData = senderDoc.data();
        senderFirstName = senderData.firstName || senderData.username || "Someone";
      }
    } else if (message.senderName) {
      senderFirstName = message.senderName;
    }

    const messageBody = message.text || (
      message.type === 'image' ? 'Sent a photo' :
      message.type === 'file' ? 'Sent a file' :
      message.type === 'set' ? 'Shared a set' :
      'You have a new message'
    );

    const notifications = members.map(async (memberId) => {
      if (memberId === message.senderId) return; // Don't send notification to sender

      const userDoc = await db.collection("users").doc(memberId).get();
      if (!userDoc.exists) {
        logger.warn(`User ${memberId} not found for notification.`);
        return;
      }

      const userData = userDoc.data();
      const fcmToken = userData.fcmToken;
      const prefs = userData.chatNotificationPrefs || {};

      if (!fcmToken || prefs[chatRoomId] === false) {
        logger.info(`Skipping notification for user ${memberId}: No FCM token or notifications disabled.`);
        return;
      }

      const payload = {
        notification: {
          title: chatRoomName,
          body: `${senderFirstName}: ${messageBody}`,
        },
        data: {
          chatRoomId: chatRoomId,
          senderFirstName: senderFirstName,
          messageBody: messageBody,
        },
        token: fcmToken,
      };

      try {
        await getMessaging().send(payload);
        logger.info(`Notification sent to ${memberId} for chat room ${chatRoomId}.`);
      } catch (err) {
        logger.error(`Failed to send notification to ${memberId} for chat room ${chatRoomId}:`, err);
      }
    });

    await Promise.allSettled(notifications);
  }
);
