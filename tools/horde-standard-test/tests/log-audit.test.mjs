import test from 'node:test';
import assert from 'node:assert/strict';
import {auditLog} from '../log-audit.mjs';
const scenario = 'lobecorp.horde-encounter-performance';
const summary = {schema:'1.0.0',scenarios:[{id:scenario,status:'PASSED',failure:null}]};
const report = {workloadCorrect:true,collectionComplete:true,encounterRemoved:true};
const header = (thread,level,logger,message) => `[date] [${thread}/${level}] [${logger}/]: ${message}`;
const stop = header('Render thread','INFO','net.minecraft.client.Minecraft','Stopping!');
const error = header('Netty Local IO #1','ERROR','net.minecraft.network.Connection','Exception caught in connection');
const root = 'java.nio.channels.ClosedChannelException';
const stack = '\tat io.netty.channel.local.LocalChannel.doWrite(LocalChannel.java:393) ~[netty.jar:?]';
const closure = [error,root,stack].join('\n');
const accepted = [stop,closure].join('\n');
const run = (text, extra={}) => auditLog(text,{summary,report,scenario,final:true,...extra});
test('normal log accepted without exemptions',()=>{assert.equal(run(stop).passed,true);assert.equal(run(stop).shutdownLogExceptions.length,0);});
test('exact completed shutdown closure records source lines and reason',()=>{const r=run(accepted);assert.equal(r.passed,true);assert.equal(r.shutdownLogExceptions.length,1);assert.equal(r.shutdownLogExceptions[0].line,2);assert.equal(r.shutdownLogExceptions[0].title,error);});
for (const [name,text] of Object.entries({
 beforeStop:closure+'\n'+stop,
 wrongLogger:accepted.replace('net.minecraft.network.Connection','other.Connection'),
 wrongThread:accepted.replace('Netty Local IO #1','Render thread'),
 wrongMessage:accepted.replace('Exception caught in connection','Exception caught in connection extra'),
 wrongRoot:accepted.replace(root,'java.lang.IllegalStateException'),
 fatal:accepted.replace('/ERROR]','/FATAL]'),
 renderError:accepted+'\n'+header('Render thread','ERROR','Renderer','draw failed'),
 cause:accepted+'\nCaused by: java.lang.IllegalStateException',
 suppressed:accepted+'\n\tSuppressed: java.nio.channels.ClosedChannelException',
 secondRoot:accepted+'\njava.lang.RuntimeException',
 laterError:accepted+'\n'+header('Netty Local IO #1','ERROR','net.minecraft.network.Connection','Unexpected error'),
 })) test(name+' blocks',()=>{assert.equal(run(text).passed,false);assert.ok(run(text).blockers.length);});
for (const [name,extra] of Object.entries({missingSummary:{summary:null},failedScenario:{summary:{...summary,scenarios:[{id:scenario,status:'FAILED'}]}},wrongScenario:{scenario:'other'},incompleteReport:{report:{...report,collectionComplete:false}},missingReport:{report:null}})) {
 test(name+' cannot accept closure',()=>{assert.equal(run(accepted,extra).passed,false);const live=run(accepted,{...extra,final:false});assert.equal(live.passed,false);assert.equal(live.shutdownLogExceptions.length,0);assert.equal(live.pending.length,1);});
}
test('live trailing header waits for root; final incomplete record blocks',()=>{const text=stop+'\n'+error;assert.equal(run(text,{final:false,summary:null}).pending.length,1);assert.equal(run(text).passed,false);});
test('completion does not permit a later new error in final audit',()=>{assert.equal(run(accepted).passed,true);assert.equal(run(accepted+'\n'+header('Render thread','ERROR','Renderer','new late error')).passed,false);});
