import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { logger } from 'firebase-functions';

initializeApp();
const db = getFirestore();

export const sendChatRoomMessageNotification = onDocumentCreated(
  "chat_rooms/{chatRoomId}/messages/{messageId}",
  async (event) => {
    const snapshot = event.data;
    const chatRoomId = event.params.chatRoomId;

    if (!snapshot) {
      logger.warn("No message data found.");
      return;
    }

    const message = snapshot.data();

    if (message.type === "system") {
      return;
    }

    const chatRoomDoc = await db.collection("chat_rooms").doc(chatRoomId).get();
    if (!chatRoomDoc.exists) {
      logger.warn(`Chat room ${chatRoomId} not found.`);
      return;
    }

    const chatRoom = chatRoomDoc.data();
    const members = chatRoom.members || [];
    const senderId = message.senderId;

    let senderFirstName = "Someone";
    if (senderId) {
      const senderDoc = await db.collection("users").doc(senderId).get();
      if (senderDoc.exists) {
        senderFirstName = senderDoc.data().firstName || senderFirstName;
      }
    }

    let messageBody = "";
    switch (message.type) {
      case "image":
        messageBody = `${senderFirstName} sent an image.`;
        break;
      case "file":
        messageBody = `${senderFirstName} shared a file.`;
        break;
      case "set":
        messageBody = `${senderFirstName} shared a set.`;
        break;
      case "text":
      default:
        messageBody = `${senderFirstName}: ${message.text || "sent a message."}`;
        break;
    }

    const notifications = members.map(async (memberId) => {
      if (memberId === senderId) return;

      const userDoc = await db.collection("users").doc(memberId).get();
      if (!userDoc.exists) return;

      const userData = userDoc.data();
      const fcmToken = userData.fcmToken;
      const prefs = userData.chatNotificationPrefs || {};

      if (!fcmToken || prefs[chatRoomId] === false) return;

      const payload = {
        notification: {
          title: chatRoom.chatRoomName || "New Message",
          body: messageBody,
        },
        data: {
          chatRoomId,
          type: message.type,
        },
        token: fcmToken,
      };

      try {
        await getMessaging().send(payload);
        logger.info(`Notification sent to ${memberId}`);
      } catch (err) {
        logger.error(`Failed to send to ${memberId}:`, err);
      }
    });

    await Promise.all(notifications);
  }
);
