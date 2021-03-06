package se.sics.gvod.hp.msgs;

import io.netty.buffer.ByteBuf;

import java.util.logging.Level;
import java.util.logging.Logger;

import se.sics.gvod.common.msgs.DirectMsgNettyFactory;
import se.sics.gvod.common.msgs.MessageDecodingException;
import se.sics.gvod.net.MsgFrameDecoder;
import se.sics.gvod.net.msgs.DirectMsg;

public class RelayRequestMsgFactory {

    public static class Request extends HpMsgFactory.Request {

        private Request() {
        }

        public static RelayRequestMsg.ClientToServer fromBuffer(ByteBuf buffer)
                
                throws MessageDecodingException {
            return (RelayRequestMsg.ClientToServer)
                    new RelayRequestMsgFactory.Request().decode(buffer);
        }

        @Override
        protected RelayRequestMsg.ClientToServer process(ByteBuf buffer) throws MessageDecodingException {

            /**
             * We create a single decoder object per request, as i don't know if a 
             * decoder object is thread-safe or not. The alternative is to have
             * a static instance in the DirectMsgNettyFactory parent class, and
             * have it shared amongst all deserializers. I don't think that will work
             * as the decoder is stateful across calls.
             */
            MsgFrameDecoder decoder= null;
            try {
                decoder = DirectMsgNettyFactory.Base.msgFrameDecoder.newInstance();
            } catch (InstantiationException ex) {
                Logger.getLogger(RelayRequestMsgFactory.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalAccessException ex) {
                Logger.getLogger(RelayRequestMsgFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
            DirectMsg message = null;
            try {
                message = (DirectMsg) decoder.parse(buffer);
            } catch (Exception ex) {
                Logger.getLogger(RelayRequestMsgFactory.class.getName()).log(Level.SEVERE, null, ex);
                throw new MessageDecodingException(ex);
            }

            return new RelayRequestMsg.ClientToServer(vodSrc, vodDest, remoteClientId, message);
        }
    }

    public static class Response extends HpMsgFactory.Response {

        private Response() {
        }

        public static RelayRequestMsg.ServerToClient fromBuffer(ByteBuf buffer)
                
                throws MessageDecodingException {
            return (RelayRequestMsg.ServerToClient)
                    new RelayRequestMsgFactory.Response().decode(buffer);
        }

        @Override
        protected RelayRequestMsg.ServerToClient process(ByteBuf buffer) throws MessageDecodingException {

            MsgFrameDecoder decoder;
            try {
                decoder = DirectMsgNettyFactory.Base.msgFrameDecoder.newInstance();
            } catch (InstantiationException ex) {
                Logger.getLogger(RelayRequestMsgFactory.class.getName()).log(Level.SEVERE, null, ex);
                throw new MessageDecodingException(ex.getMessage());
            } catch (IllegalAccessException ex) {
                Logger.getLogger(RelayRequestMsgFactory.class.getName()).log(Level.SEVERE, null, ex);
                throw new MessageDecodingException(ex.getMessage());
            }
            DirectMsg message = null;
            try {
                message = (DirectMsg) decoder.parse(buffer);
            } catch (Exception ex) {
                Logger.getLogger(RelayRequestMsgFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
            return new RelayRequestMsg.ServerToClient(vodSrc, vodDest,
                    remoteClientId, message);
        }

    }
}
