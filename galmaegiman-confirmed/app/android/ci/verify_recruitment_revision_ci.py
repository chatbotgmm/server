#!/usr/bin/env python3
"""Require real native recruitment, shared-state and timed home-motion evidence."""
import argparse
import json
import math
import re
from pathlib import Path
import xml.etree.ElementTree as ET
from verify_registration_ci import check, emit, sha256, png_dimensions
from verify_feedback_stage1_ci import verify_png, rect, inside, same_rect, overlap, MODE_PROFILES, verify_mode_profile
from verify_visual_approval import fingerprint, git

HERE = Path(__file__).resolve().parent
TRAY_INK = {'LOW':[19,76,749,435],'MID':[19,76,750,436],'HIGH':[19,72,750,436]}
TRAYS = {f'recruitment/tray-{kind}-v1.png' for kind in ('low','mid','high')}
SUITES = {
 'NativeTypographyAlignmentTest': ['namesWrapAtWordsAndIdenticalMeasuresDoNotResetText','plaquesAndModeStacksStayCenteredAcrossExistingProfiles'],
 'BattleItemTypographyTest': ['narrowAndWideCardsKeepWordsAndCenteredLabels','sdk26CompatKeepsWordsWithinActualCells','sdk24KeepsCompatConfigurationAndAction'],
 'BattleReplayStabilityTest': ['identicalSnapshotPerformsNoMoreDiskWrites', 'byteOverflowWritesMarkerOnceAndNeverPretendsComplete', 'frameOverflowStopsWritesOnce', 'corruptNullFramesAndInvalidTeamsAreExcluded', 'actualSpeciesIdentityNeverFallsBackToCombatAncestor', 'recordedStageTintAndScaleRemainFixed', 'preparationRevisionIsNotEvidenceOfMissingBattleStart', 'identicalTerminalCanRetryAfterIndexWriteFailure', 'restartCleansCrashOrphansAndTemporaryFiles', 'modeAndResultLabelsNeverGuessUnknownValues'],
 'NativeResultReassessmentSwipeTest': ['iconWaitsForServerAndOnlyMapsNewEligibleExactSpecies', 'oldOwnedConcurrentAndUnprovenKeysNeverBecomeThisAcquisition', 'unsupportedServerExplainsAndReadOnlyRetryDoesNotFakeSuccess', 'malformedOrCrossSpeciesReceiptKeysFailClosed', 'serverCooldownCannotBeBypassedByWaitingOnClientClock', 'horizontalSwipesMoveThreeCardsWithoutDossierAndKeepBackSelection', 'cancelledAndVerticalGesturesDoNotPageOrOpenDossier', 'cancelIsReadOnlyAndConfirmedRetryUsesSameExactDurableIntent', 'multipleNewInstancesRequireChooserAndNeverSelectOldOwnedKey', 'successRefreshesAuthoritativeStateAndArtWithoutLosingPageOrSelection', 'convertingOneOfAggregatedCopiesPreservesEveryUnconvertedCopy', 'closingOverlayDuringMutationStillReconcilesTheOwnedResultWithoutReopening', 'retryOpenedFromAnotherCardReplacesTheOriginalSourceKeyOnly', 'lateEligibilityAndStateReadsCannotPaintDetachedOrOtherAccountResults', 'navigatingDuringMutationDoesNotReopenOrRepaintAnAbandonedResult', 'captureRefreshTargetAndSwipeAtRealDensityAndFont130'],
 'BattleReplayStoreTest': ['eleventhCompletionPrunesOldest', 'duplicateCompletionDoesNotReorderOrReward', 'accountSwitchNeverLeaksRecords', 'restartRetainsDraftAndCompletedTimeline', 'failedPartialAndResultOnlyAreNotCompleted', 'regressionRejectedAndMissingBeginningDisclosed', 'capturesAreImmutableAndRemovePrivateSubmissionData', 'replaySnapshotCannotBeRecaptured', 'deterministicTimelineAndDefensiveCopies', 'stopDisposeAndNoEconomicApiDependencies'],
 'NativeBattleReplayReviewTest': ['captureReadOnlyTimelineAndDisposeAcrossProfiles', 'changingAccountClearsVisibleReplay', 'realTimerAdvancesPausesAndStopsOnBack', 'recordsLoadPreservesDirtyTeamExitGuard', 'serialWritesKeepCapturedAccountAcrossRapidSwitches', 'malformedEffectEntriesCannotCrashDefensiveRenderer'],
 'NativeRealtimeIdentityRevisionTest': ['backendOwnedAndRankThumbnailIdentitiesResolveWithoutMutatingKeys', 'invalidAndHiddenIdentitiesNeverBorrowAnotherCharacter', 'selectedAndCandidateArtDecodeForSeparateOwnedCopies', 'ownedReplacementSendsInstanceKeyAndLocksUntilServerReply', 'readyAndExpiredOwnedPreparationRemainLocked', 'compactAcceptanceNeverAutoSubmitsAndRetainsDeadlineGuard', 'captureBackendIdentityAndCompactControls','replayObservationPrecedesAnimationCoalescingAndUnchangedReturns','replayKeepsEveryAcceptedSnapshotWhileDisplayCoalesces'],
 'BattleUtilitiesChartRevisionTest': ['utilitiesStayInCommandPanelAtSmallInsetAndFont130', 'itemButtonsShowOnlyNameAndQuantityWithDisabledSemantics', 'statusNamesExcludeEffectNumbers', 'matrixUsesCanonicalAttackDefenseOrientationAndServerOverride', 'typeDialogRetainsCloseAndTurnLease', 'captureSmallInsetFont130AndTypeMatrix','itemMenuAutosizeIsSafeOnSdk24','itemMenuAutosizeHasNativeTextBoundsOnSdk26'],
 'NativeExternalMusicTest': ['externalMusicLaunchAndClicksNeverRequestFocusOrStartBgm','noExternalMusicUsesNormalFocusAndLocalBgm','pauseResumeRechecksExternalMusicAndRejectsBackgroundPlayback','externalMusicVolumeAndMuteNeverChangeStoredPreferencesOrTakeFocus','callModeAndHeadphoneDisconnectSilenceGameUntilSafeResume','focusLossAndStaleGainCannotRestartWhilePaused','permanentMediaTakeoverAllowsFocusFreeEffectsButTransientAndCallsStaySilent'],
 'NativeTutorialModeRoutesTest': ['firstBattleGuideFollowsCategoryThenStoryModeWithoutBacktracking'],
 'NativeModeCategoryNavigationTest': ['captureModeCategoriesAcrossShortSafeAreas','storyCategoryKeepsExistingStoryAndNaturalBackRoutes','pvpSubroutesReturnToTheirCategoryWithoutChangingParty','missingPvpPartyStillRequiresConfirmationAndReturnsToPvp','repeatedAndBusyCategoryClicksKeepCurrentPageAndEpoch'],
 'StorySelectionCardTest': ['wholeCardEntersExactlyOnceAndBackReturnsToChapters','lockedCardCannotEnterEvenThroughProgrammaticClick','busyCardDoesNotConsumeItsLaterValidSelection','obsoleteButtonsDisappearButBattleStartRemains','canonicalStageSceneAndRawLockDataStayUnchanged','captureWholeCardsAcrossShortFontAndCutoutSafeAreas'],
 'NativeTeamInsertionPreviewTest': ['horizontalInsertionPreviewsBeforeDropAndCancelRestoresEveryCard','verticalStoryInsertionPreviewsAndCancelKeepsServerOrder','storyHandleHasNoModalAndAccessibleMoveSavesOnceWhilePending'],
 'NativeFullscreenReviewTest': ['decorationFillsWindowButContentKeepsEveryInset','realNativePagesFillEdgesAndRetainSafeContent','liveInsetsKeepKeyboardAndVisibleBarsAwayFromControls'],
 'NativeTeamCharacterPanelTest': ['captureSolidPortraitsAndCompactPagerAcrossProfiles','handleTapAndLongPressNeverOpenReorderModal','accessibleReorderIsInlineDraftOnlyAndBusyGuarded'],
 'CurrencyDisplayReviewTest': ['sharedDisplayRetainsClickAndBusySemantics','insetSurfaceKeepsReadableStatesWithinBounds','renderMeasuredBalancesAcrossSizes'],
 'NativeRecruitmentCounterReviewTest': ['renderDistinctCounterTraysAndActionStatesAcrossSizes', 'preservesCooldownBoundaryAndSelectionWithoutWrites','claimRequestStaysLockedAcrossTicksAndRefreshesCooldown','preservesNewOperationLockWhenClaimRefreshBecomesStale'],
 'ReleaseHomeMotionTest': ['criticalReturnIsSlowBoundedAndNeverCrossesRest','meshOnlyMovesWaterVerticesAndRetainsItsBuffers','realArtChangesOnlyInsideWaterAndReducedMotionIsPixelStable','dragKeepsFeetAndShadowFixedAndCancelsOnPauseSystemOffAndDetach','regrabCancelAndSecondPointerDoNotLeaveAnActiveDrag','blankCenterDragUsesRealDispatchAndControlsKeepTheirGestures','timedPhoneWaterFramesAdvanceAndResumeAfterPauseAndHomeReentry','disabledHomeControlKeepsImageAndAppliesSharedToneOnce','currencyInsetsKeepMaximumAmountsAndShopTargetsAtLargeFont','currencyMaximumAmountsKeepFourteenSpAcrossSafePhoneWidths'],
 'NativeThemeStateTest': ['nativeAudioRailsRemainVisibleAtZeroHalfAndFullVolume','settingsBackgroundAlphaFilterAndAsyncCallbackArePreserved','soundRestoreAccountGuardAndCloseRemainNativeAndDoNotWrite','buttonsPreservePressSelectionDisabledAlphaAndClearableFilters','sharedButtonCancelAndDisableNeverActivateAndReenableRestoresInput'],
}
PROFILES = {'915x412':(915,412),'1006x411':(1006,411),'1280x720':(1280,720),'915x412-font130':(915,412),'780x360-font130':(780,360)}
STATES = ('low','mid','high','cooldown','insufficient','maximum','unlimited')

def read(path): return json.loads(Path(path).read_text())

def tray_lock():
    lock = read(HERE / 'recruitment-assets-lock.json')
    check(lock.get('schema') == 'recruitment-tray-assets/v1', 'Unknown tray asset lock')
    entries = lock.get('files', [])
    check(len(entries) == 3 and {e['path'] for e in entries} == TRAYS, 'Exactly three tray assets required')
    for e in entries:
        check(set(e) == {'path','bytes','sha256','dimensions'}, 'Wrong tray lock fields')
        check(e['dimensions'] == [768,512] and type(e['bytes']) is int and e['bytes'] > 10000, 'Wrong tray asset shape')
        check(type(e['sha256']) is str and len(e['sha256']) == 64 and all(c in '0123456789abcdef' for c in e['sha256']), 'Invalid tray SHA256')
    check(len({e['sha256'] for e in entries}) == 3, 'Trays must be three distinct artworks')
    return entries

def verify_trays(read_bytes, entries):
    check(len(entries) == 3 and {e['path'] for e in entries} == TRAYS, 'Tray allowlist changed')
    for entry in entries:
        data=read_bytes(entry['path'])
        check(len(data)==entry['bytes'] and sha256(data)==entry['sha256'], 'Tray asset hash/bytes mismatch: '+entry['path'])
        check(png_dimensions(data)==(768,512) and data[25]==6, 'Tray must be transparent-capable RGBA 768x512 PNG')
    return {'files':len(entries),'sha256':{e['path']:e['sha256'] for e in entries}}

def junit(directory):
    required = dict(SUITES)
    required.update({'JournalThemeTest': [], 'NativeEffectsPresentationTest': [], 'NativeRecruitmentProbabilityTest': []})
    for suite, methods in required.items():
        full='org.dpdns.gmm.'+suite
        root=ET.parse(Path(directory)/('TEST-'+full+'.xml')).getroot()
        cases=root.findall('testcase')
        check(root.get('name')==full and cases, 'Missing required suite: '+full)
        names=[c.get('name') for c in cases]
        check(len(names)==len(set(names)) and set(methods)<=set(names), 'Missing/duplicate revised method: '+full)
        check(all(c.get('classname')==full and not any(c.find(k) is not None for k in ('failure','error','skipped')) for c in cases), 'Revised suite failed/skipped: '+full)
    return required

def verify_motion(root):
    names=['home-drag-center-rest.png','home-drag-center-held.png','home-drag-center-return-t0160.png','home-drag-center-settled.png','home-water-phone-t0150.png','home-water-phone-t1100.png']
    images=[verify_png(root/name,915,412) for name in names]
    m=read(root/'home-water-motion.json')
    check(m.get('schema')=='home-water-motion-v1' and m.get('width')==915 and m.get('height')==412, 'Wrong native motion profile')
    check(m.get('firstFrame')==names[-2] and m.get('secondFrame')==names[-1], 'Wrong timed frame identities')
    check(type(m.get('firstClockMillis')) in (int,float) and type(m.get('secondClockMillis')) in (int,float)
          and m['secondClockMillis']-m['firstClockMillis']>500, 'Native clock failed to advance')
    check(m.get('minimumRgbDelta')==12 and m.get('minimumChangedPixels')==100
          and type(m.get('visibleWaterChangedPixels')) is int and m['visibleWaterChangedPixels']>100, 'Visible masked water motion missing')
    check(images[-1]['sha256']!=images[-2]['sha256'], 'Timed native water frames identical')
    check(images[0]['sha256']!=images[1]['sha256'] and images[1]['sha256']!=images[2]['sha256'], 'Drag/return frames identical')
    images.append(verify_png(root/'home-disabled-play-image.png',915,412))
    images.append(verify_png(root/'home-currency-max-font130.png',915,412))
    return images

def verify_counter(root):
    directory=root/'recruitment-counter'
    expected={state+'-'+profile:(state,size) for state in STATES for profile,size in PROFILES.items()}
    check({p.stem for p in directory.glob('*.png')}==set(expected)|{'claim-requesting-915x412','claim-refreshed-915x412'}, 'Recruitment capture inventory must be exactly 37')
    geometry,findings=read(directory/'geometry.json'),read(directory/'findings.json')
    check(set(geometry)==set(findings)==set(expected), 'Recruitment measurements/findings inventory mismatch')
    claim=read(directory/'claim-state.json')
    expected_claim={'requestingClaimEnabled':False,'requestingBusy':True,'requestsAfterDuplicateTap':1,
                    'refreshingClaimEnabled':False,'refreshingBusy':True,'refreshedClaimEnabled':False,'refreshedBusy':False,'refreshedCredits':230,'refreshedCooldownMillis':3600000}
    check(all(type(claim.get(k)) is type(v) and claim[k]==v for k,v in expected_claim.items()), 'Claim request unlocked, duplicated or refreshed incorrectly')
    check(claim.get('calls')==['GET draw','POST claim','GET native/state','GET draw'], 'Unexpected claim request sequence')
    images=[verify_png(directory/name,915,412) for name in ('claim-requesting-915x412.png','claim-refreshed-915x412.png')]
    for stem,(state,size) in expected.items():
        images.append(verify_png(directory/(stem+'.png'),*size))
        m=geometry[stem]
        check(findings[stem]==[], 'Recruitment geometry finding: '+stem)
        check(m.get('actualNativeViews') is True and m.get('apiWrites')==0, 'Synthetic or mutating recruitment evidence')
        check(all(type(m.get(k)) is int and m[k]>0 for k in ('controls','labels','independentOverlapChecks')), 'Missing recruitment measured geometry')
        trays=m.get('trays',[])
        check(len(trays)==3 and {t['kind'] for t in trays}=={'LOW','MID','HIGH'}, 'Missing distinct tray views')
        baselines=[]
        for t in trays:
            check(t['path']=='recruitment/tray-'+t['kind'].lower()+'-v1.png'
                  and type(t['artWidth']) in (int,float) and type(t['artHeight']) in (int,float)
                  and t['artWidth']>0 and t['artHeight']>=64, 'Wrong native tray asset identity')
            source=rect(t.get('sourceBounds'))
            check(source==TRAY_INK[t['kind']], 'Tray alpha silhouette bounds changed')
            check(abs(t['artWidth']/t['artHeight']-(source[2]-source[0])/(source[3]-source[1]))<.001, 'Tray artwork stretched')
            check(type(t.get('visibleBaseline')) in (int,float) and math.isfinite(t['visibleBaseline']), 'Missing tray counter baseline')
            baselines.append(t['visibleBaseline'])
        check(max(baselines)-min(baselines)<=1, 'Trays do not sit on one counter')
        check(m.get('selectedKind') in ('LOW','MID','HIGH') and isinstance(m.get('totalText'),str) and m['totalText'], 'Missing selection/total state')
        check(type(m.get('claimEnabled')) is bool and type(m.get('submitEnabled')) is bool, 'Missing button enabled state')
        if state in ('low','mid','high'): check(m['selectedKind']==state.upper(), 'Wrong selected tray')
        if state=='cooldown': check(m['claimEnabled'] is False, 'Cooldown claim must be disabled')
        if state=='insufficient': check(m['submitEnabled'] is False, 'Insufficient submit must be disabled')
    return images

def verify_currency(root):
    directory=root/'currency-display'
    check({p.stem for p in directory.glob('*.png')}==set(PROFILES), 'Currency capture inventory must be exactly five')
    geometry,findings=read(directory/'geometry.json'),read(directory/'findings.json')
    check(set(geometry)==set(findings)==set(PROFILES), 'Currency measurements/findings inventory mismatch')
    images=[]
    for name,(width,height) in PROFILES.items():
        images.append(verify_png(directory/(name+'.png'),width,height))
        m=geometry[name]
        check(m.get('passed') is True and findings[name]==[], 'Currency native layout failed: '+name)
        displays=m.get('displays',[])
        check(len(displays)==3 and {d['kind'] for d in displays}=={'snack','peanut','credits'}, 'Missing distinct resource display')
        for d in displays:
            keys=('width','height','textWidth','availableWidth','left','top','right','bottom')
            check(all(type(d.get(k)) in (int,float) and math.isfinite(d[k]) for k in keys), 'Missing currency measurements')
            check(d['width']>=48 and d['height']>=48, 'Currency touch target below 48dp')
            check(0<d['textWidth']<=d['availableWidth']<=d['width'], 'Currency ink exceeds available width')
            check(0<=d['left']<d['right']<=width and 0<=d['top']<d['bottom']<=height, 'Currency display outside viewport')
            check(abs(d['right']-d['left']-d['width'])<=1 and abs(d['bottom']-d['top']-d['height'])<=1, 'Currency bounds disagree with view size')
            check(isinstance(d.get('text'),str) and d['text'], 'Missing live currency text')
    return images

TEAM_PROFILES = {
 'phone-915x412':(915,412,160,1.0,[0,0,0,0]),
 'short-780x319':(780,319,160,1.0,[0,0,0,0]),
 'short-highdensity-2048x838':(2048,838,420,1.0,[73,26,26,26]),
 'short-font130-915x374':(915,374,160,1.3,[0,0,0,0]),
}

def verify_team(root):
    directory=root/'team-character-panels'
    expected={name+'-'+scene+'-'+page+'.png':(name,scene,page,profile)
              for name,profile in TEAM_PROFILES.items() for scene in ('party-story','party-pvp') for page in ('first','last')}
    check({p.name for p in directory.glob('*.png')}==set(expected), 'Team capture inventory must be exactly 16')
    manifest,metrics,findings=read(directory/'manifest.json'),read(directory/'metrics.json'),read(directory/'findings.json')
    check(set(metrics)==set(findings)==set(expected), 'Missing team measurements/findings')
    check(manifest.get('schemaVersion')==1 and manifest.get('suite')=='team-character-panels'
          and manifest.get('baseline')=='680cc3565aafddcd262541faed44b6a333dde912'
          and manifest.get('requiredCaptureCount')==16, 'Wrong team manifest')
    captures=manifest.get('captures',[])
    check(len(captures)==16 and {c['filename'] for c in captures}==set(expected), 'Wrong team manifest capture identities')
    for c in captures:
        name,scene,page,_=expected[c['filename']]
        check((c.get('profile'),c.get('scene'),c.get('page'))==(name,scene,page), 'Wrong team manifest profile/page')
    images=[]
    approved={f'{prefix}{i}' for prefix,n in (('D',8),('E',8),('I',9),('T',8)) for i in range(1,n+1)}
    for file,(name,scene,page,profile) in expected.items():
        width,height,dpi,font,insets=profile
        images.append(verify_png(directory/file,width,height))
        m=metrics[file];p=m.get('profile',{})
        check(findings[file]==[] and m.get('scene')==scene and m.get('apiWrites')==0 and m.get('registrationViews')==0, 'Team native regression: '+file)
        check((p.get('widthPx'),p.get('heightPx'),p.get('densityDpi'),p.get('fontScale'))==(width,height,dpi,font)
              and p.get('insetsPx')==dict(zip(('left','top','right','bottom'),insets)), 'Wrong team profile')
        safe=[insets[0],insets[1],width-insets[2],height-insets[3]]
        check(m.get('solidPortraitCount')==6 and m.get('portraitColor')=='ff87999b' and m.get('modalVisible') is False, 'Solid portraits/modal state regressed')
        arts=m.get('artworks',[])
        check(len(arts)==6, 'Missing whole character artwork')
        for art in arts:
            view=rect(art['viewPx']);target=rect(art['targetPx'])
            check(inside(safe,view) and same_rect(view,art['visiblePx']), 'Clipped team portrait')
            check(art.get('id') in approved and art.get('expectedSource')=='game-art/duel/sprites/'+art['id']+'_s0_front.webp'
                  and art.get('status')=='', 'Wrong character source identity')
            check(type(art.get('bitmapWidth')) is int and type(art.get('bitmapHeight')) is int
                  and art['bitmapWidth']>0 and art['bitmapHeight']>0, 'Missing character bitmap')
            check(inside([0,0,view[2]-view[0],view[3]-view[1]],target)
                  and abs((target[2]-target[0])/(target[3]-target[1])-art['bitmapWidth']/art['bitmapHeight'])<.001, 'Character cropped/distorted')
        toolbar,area=rect(m['toolbarPx']),rect(m['candidateAreaPx'])
        check(inside(safe,toolbar) and inside(safe,area) and abs(toolbar[3]-area[1])<.01
              and toolbar[2]-toolbar[0]<=area[2]-area[0], 'Pager detached or clipped')
        check(isinstance(m.get('targetLabel'),str) and '교체' in m['targetLabel'], 'Missing replacement target label')
        label=m.get('pageLabel','');match=re.fullmatch(r'([1-9][0-9]*) / ([1-9][0-9]*)',label)
        check(match is not None, 'Wrong page label')
        current,total=map(int,match.groups())
        check(total>1 and current==(1 if page=='first' else total), 'Wrong first/last page state')
        controls=m.get('controls',[])
        check(controls and m.get('liveLabels'), 'Missing team controls/labels')
        for control in controls:
            bounds=rect(control['boundsPx'])
            check(inside(safe,bounds) and bounds[2]-bounds[0]>=48*dpi/160-.5 and bounds[3]-bounds[1]>=48*dpi/160-.5, 'Team touch geometry regressed')
        for label in m['liveLabels']:check(inside(safe,rect(label['boundsPx'])), 'Team live label clipped')
    reorder=read(directory/'inline-reorder.json')
    check(set(reorder)=={'party-story','party-pvp'}, 'Missing inline reorder mode')
    for data in reorder.values():
        check(data.get('modalVisible') is False and all(data.get(k) is True for k in ('draftOnly','busyGuard','discardRestores')), 'Inline reorder mutation/modal/guard regression')
    return images

def fullscreen_profiles():
    return {page+'-'+str(width)+'x'+str(height)+'-font'+str(font)+'-'+side:(width,height,side)
            for page in ('recruit','shop','craft','team') for width,height,font in ((915,412,100),(780,360,130),(1280,720,100)) for side in ('left','right')}

def verify_fullscreen(root):
    directory=root/'fullscreen'; expected=fullscreen_profiles()
    check({p.stem for p in directory.glob('*.png')}==set(expected), 'Fullscreen capture inventory must be exactly 24')
    metrics=read(directory/'metrics.json')
    check(type(metrics) is list and len(metrics)==24 and {m['name'] for m in metrics}==set(expected), 'Missing/duplicate fullscreen metrics')
    images=[]
    for m in metrics:
        width,height,side=expected[m['name']]
        images.append(verify_png(directory/(m['name']+'.png'),width,height))
        left,right=(36,10) if side=='left' else (10,36)
        check(m.get('background')==f'[0,0][{width},{height}]', 'Decorative background does not fill root')
        check(m.get('content')==f'[{left},12][{width-right},{height-24}]', 'Fullscreen fix changed safe content insets')
        check(m.get('allEdgePixelsMatchBackground') is True, 'Exposed screen edge differs from native scene')
    return images

def verify_insertion(root):
    directory=root/'team-insertion-preview'
    names=('party-before','party-preview','party-cancel','party-drop','story-list-before','story-list-preview','story-list-cancel')
    check({p.stem for p in directory.glob('*.png')}==set(names), 'Missing/extra live insertion frames')
    images=[verify_png(directory/(name+'.png'),915,412) for name in names]
    check(images[0]['sha256']!=images[1]['sha256'] and images[4]['sha256']!=images[5]['sha256'], 'Insertion preview did not change native pixels')
    for scene in ('party','story-list'):
        m=read(directory/(scene+'.json'))
        check(m.get('schemaVersion')==1 and m.get('widthPx')==915 and m.get('heightPx')==412, 'Wrong insertion profile')
        check(type(m.get('previewOffsetPx')) in (int,float) and math.isfinite(m['previewOffsetPx']) and m['previewOffsetPx']>0,
              'Missing measured insertion displacement')
        check(m.get('repeatedMoves')==12 and m.get('cancelRestored') is True and m.get('apiWrites')==0 and m.get('findings')==[], 'Insertion repeated-move/cancel/write regression')
        if scene=='party':
            check(m.get('draftUnchangedBeforeDrop') is True and m.get('outsideDropRestored') is True, 'Party draft changed before valid drop')
            order=m.get('dropOrder')
            check(type(order) is list and len(order)==3 and all(isinstance(x,str) and x for x in order) and len(set(order))==3, 'Missing distinct dropped party order')
        else: check(m.get('stateUnchangedBeforeDrop') is True, 'Story server order changed during preview')
    order=read(directory/'story-order.json')
    check(order.get('schemaVersion')==1 and order.get('modalVisible') is False and order.get('saveCount')==1
          and order.get('busyBlocksDuplicate') is True and order.get('savedKeys')==['o1','o3','o2'] and order.get('findings')==[],
          'Story inline order did not preserve one pending native save')
    return images

def story_card_profiles():
    return {f'story-cards-{w}x{h}-font{font}-cutout':(w,h,font/100) for w,h,font in ((915,412,100),(1280,720,100),(915,320,100),(915,412,130))}

def verify_story_cards(root):
    directory=root/'story-cards';expected=story_card_profiles()
    check({p.stem for p in directory.glob('*.png')}==set(expected), 'Story cards require four cutout/font/short profiles')
    metrics,findings=read(directory/'metrics.json'),read(directory/'findings.json')
    check(set(metrics)==set(findings)==set(expected), 'Missing story-card measurements/findings')
    images=[]
    for name,(width,height,font) in expected.items():
        images.append(verify_png(directory/(name+'.png'),width,height));m=metrics[name]
        check(findings[name]==[] and m.get('actualNativeViews') is True and m.get('cards')==3, 'Story card native finding/card count')
        check(m.get('width')==width and m.get('height')==height and type(m.get('fontScale')) in (int,float) and abs(m['fontScale']-font)<.001
              and m.get('safeInsetsPx')==[28,8,0,12], 'Wrong story safe-area profile')
        check(m.get('measuredWidth')==width and m.get('measuredHeight')==height and m.get('safeWidth')==width-28 and m.get('safeHeight')==height-20, 'Story native bounds disagree with cutouts')
        check(m.get('fixtureWrites')==0 and m.get('dialog') is False, 'Story capture mutated state or opened unexpected modal')
        check(all(type(m.get(k)) is int and m[k]>0 for k in ('actions','boundsChecks','textLabels')) and type(m.get('inlineChoices')) is int and m['inlineChoices']>=0, 'Story card touch/text/bounds checks missing')
    return images

def verify_mode_navigation(root):
    directory=root/'feedback-mode-navigation'
    expected={name+'-'+route+'.png':(name,route,profile) for name,profile in MODE_PROFILES.items() for route in ('modes','modesStory')}
    check({p.name for p in directory.glob('*.png')}==set(expected), 'Mode navigation requires all 16 root/story captures')
    metrics,findings,manifest=read(directory/'metrics.json'),read(directory/'findings.json'),read(directory/'manifest.json')
    check(set(metrics)==set(findings)==set(expected), 'Missing category measurements/findings')
    check(manifest.get('schemaVersion')==1 and manifest.get('suite')=='feedback-mode-navigation'
          and manifest.get('baseline')=='680cc3565aafddcd262541faed44b6a333dde912'
          and manifest.get('routes')==['modes','modesStory'], 'Wrong category manifest')
    captures=manifest.get('captures',[]); profiles=manifest.get('profiles',[])
    check(len(captures)==16 and {c['filename'] for c in captures}==set(expected), 'Category capture manifest incomplete')
    check(len(profiles)==8 and {p['name'] for p in profiles}==set(MODE_PROFILES), 'Category profile manifest incomplete')
    for p in profiles:verify_mode_profile(p,p['name'],MODE_PROFILES[p['name']],manifest=True)
    for c in captures:
        name,route,(w,h,*_)=expected[c['filename']]
        check((c.get('profile'),c.get('route'),c.get('widthPx'),c.get('heightPx'))==(name,route,w,h), 'Wrong category manifest dimensions/route')
    images=[]
    for file,(name,route,(width,height,dpi,font,insets)) in expected.items():
        images.append(verify_png(directory/file,width,height));m=metrics[file]
        check(findings[file]==m.get('findings')==[] and m.get('actualNativeViews') is True and m.get('route')==route and m.get('profile')==name, 'Category native finding or wrong route')
        check((m.get('screenWidthPx'),m.get('screenHeightPx'),m.get('densityDpi'))==(width,height,dpi)
              and type(m.get('fontScale')) in (int,float) and abs(m['fontScale']-font)<.001, 'Wrong category device profile')
        safe=[insets[0],insets[1],width-insets[2],height-insets[3]]
        check(same_rect(m['safeBoundsPx'],safe) and m.get('scrollContainers')==0
              and m.get('apiWrites')==0 and m.get('fixtureReads')==[], 'Category scrolled, fetched or lost safe insets')
        check(type(m.get('touchChecks')) is int and m['touchChecks']>=2 and type(m.get('textChecks')) is int and m['textChecks']>=2, 'Category geometry checks missing')
        cards=m.get('cards',{});labels={'story':'스토리','pvp':'대전'} if route=='modes' else {'story':'스토리모드','adventure':'모험\n준비 중'}
        check(set(cards)==set(labels), 'Category must contain exactly the two requested choices')
        boxes=[]
        for key,label in labels.items():
            c=cards[key];box=rect(c['boundsPx']);glyph=rect(c['glyphBoundsPx']);reading=rect(c['readingBoundsPx']);boxes.append(box)
            check(c.get('text')==label and c.get('enabled') is (key!='adventure') and c.get('motif')==({'story':0 if route=='modes' else 3,'pvp':1,'adventure':2}[key]), 'Wrong category label/availability/motif')
            check(inside(safe,box) and box[2]-box[0]>=48*dpi/160-.5 and box[3]-box[1]>=48*dpi/160-.5
                  and inside(box,reading) and inside(reading,glyph), 'Category ink/touch/ornament bounds regression')
        check(not overlap(boxes[0],boxes[1]), 'Category click targets overlap')
        texts=m.get('texts',{})
        check(type(texts) is dict and len(texts)>=2, 'Missing measured category text')
        for t in texts.values():
            box=rect(t['boundsPx']);visible=rect(t['visibleBoundsPx']);glyph=rect(t['glyphBoundsPx'])
            check(inside(safe,box) and same_rect(box,visible) and inside(visible,glyph), 'Category text clipped')
            check(t.get('ellipsisCount')==0 and t.get('autoSizeTextType')==0 and type(t.get('textScaleX')) in (int,float)
                  and abs(t['textScaleX']-1)<.0001 and type(t.get('textSizePx')) in (int,float) and t['textSizePx']>0, 'Category text shrunk or ellipsized')
    return images

def verify_realtime_identity(root):
    d=root/'realtime-identity-revision'; metrics=read(d/'metrics.json'); images=[]
    profiles={k:v for k,v in PROFILES.items() if k!='780x360-font130'}
    stems=('prepare-owned','prepare-rank-owned','accept-compact','augment-compact','augment-selected-compact')
    check(set(metrics)=={stem+'-'+profile for stem in stems for profile in profiles}, 'Wrong realtime identity evidence inventory')
    for stem in stems:
        for profile,(w,h) in profiles.items():
            name=stem+'-'+profile; m=metrics[name]
            check(m.get('actualNativeViews') is True and m.get('backendCompatiblePayload') is True and m.get('apiWrites')==0, 'Identity evidence must use read-only native backend-shaped fixture')
            check(m.get('width')==w and m.get('height')==h and m.get('fontScale')==(1.3 if 'font130' in profile else 1.0), 'Wrong realtime viewport/font')
            check(type(m.get('actions')) is int and m['actions']>=1, 'Missing measured realtime controls')
            identities=m.get('identities')
            if stem.startswith('prepare'):
                check(type(identities) is list and len(identities)==6, 'Missing preparation identity pixels')
                for i,(identity,species) in enumerate(zip(identities,('D1','D2','D3','D1','D1','D2'))):
                    tag='realtime-prepare-'+('slot-' if i<3 else 'candidate-')+str(i%3)
                    check(identity==dict(tag=tag,species=species,path='/duel/sprites/'+species+'_s0_front.webp',pixelIdentityVerified=True,status=''), 'Wrong owned-copy original artwork identity')
            else: check(identities==[], 'Unexpected identity inventory')
            images.append(verify_png(d/(name+'.png'),w,h,minimum_bytes=1000))
    return images

def verify_battle_utilities(root):
    d=root/'battle-ui-revision'; metrics=read(d/'observations.json'); images=[]
    expected={f'battle-{pane}-{w}-inset-font130.png':(w,h) for w,h in ((640,360),(915,412)) for pane in ('menu','items')}
    check(type(metrics) is list and len(metrics)==4 and {m.get('capture') for m in metrics}==set(expected), 'Wrong battle utility observations inventory')
    for m in metrics:
        w,h=expected[m['capture']]
        check(m.get('width')==w and m.get('height')==h and type(m.get('fontScale')) in (int,float) and abs(m['fontScale']-1.3)<.0001 and m.get('status')=='화상', 'Battle utility viewport/status regression')
        check(all(type(m.get(k)) in (int,float) and math.isfinite(m[k]) for k in ('utilityLeft','panelLeft')) and m['utilityLeft']>=m['panelLeft'], 'Battle utilities escaped command panel')
        buttons=m.get('buttons');check(type(buttons) is list and len(buttons)>0, 'Missing measured battle buttons')
        for b in buttons:
            check(type(b.get('text')) is str and b['text'].strip() and type(b.get('enabled')) is bool, 'Missing battle control label/state')
            check(all(type(b.get(k)) in (int,float) and math.isfinite(b[k]) and b[k]>=48 for k in ('width','height')), 'Battle control below 48dp')
        images.append(verify_png(d/m['capture'],w,h,minimum_bytes=1000))
    for w,h in ((640,360),(915,412)):
        images.append(verify_png(d/f'battle-types-{w}-font130.png',w-24,h-16,minimum_bytes=1000))
    for w,h,sdk in ((640,360,35),(915,412,35),(640,360,26)):
        images.append(verify_png(d/f'battle-items-word-aligned-{w}-sdk{sdk}.png',w,h,minimum_bytes=1000))
    return images

def verify_replay(root):
    d=root/'battle-replay'; manifest=read(d/'manifest.json'); metrics=read(d/'metrics.json'); findings=read(d/'findings.json')
    expected={name+'-'+frame+'.png':(p[0],p[1],frame) for name,p in TEAM_PROFILES.items() for frame in ('start','end')}
    check(manifest.get('schemaVersion')==1 and manifest.get('suite')=='battle-replay' and manifest.get('requiredCaptureCount')==8, 'Wrong replay manifest')
    captures=manifest.get('captures');check(type(captures) is list and len(captures)==8 and {v.get('filename') for v in captures}==set(expected), 'Missing replay capture inventory')
    check(set(metrics)==set(expected) and set(findings)==set(expected) and all(v==[] for v in findings.values()), 'Replay findings or metrics missing')
    for capture in captures:
        w,h,_=expected[capture['filename']];check(capture.get('widthPx')==w and capture.get('heightPx')==h, 'Wrong replay manifest dimensions')
    images=[]
    for name,(w,h,frame) in expected.items():
        m=metrics[name];at_end=frame=='end'
        check(m.get('widthPx')==w and m.get('heightPx')==h and m.get('position')==(2 if at_end else 0) and m.get('frameCount')==3, 'Wrong replay timeline position')
        check(m.get('apiCalls')==0 and m.get('stateUnchanged') is True and m.get('readOnlyScene') is True, 'Replay wrote active state or API')
        check(m.get('previousEnabled') is at_end and m.get('nextEnabled') is (not at_end), 'Replay boundary controls incorrect')
        images.append(verify_png(d/name,w,h,minimum_bytes=1000))
    return images

def reassessment_profiles():
    return {f'{w}x{h}-dpi{dpi}-font{font}-{state}':(w,h,dpi,font/100,state)
            for w,h,dpi in ((915,412,160),(780,360,160),(2048,838,360),(1280,720,160))
            for font in (100,130) for state in ('eligible','mid-drag','swiped')}

def verify_reassessment(root):
    d=root/'result-reassessment'; metrics=read(d/'observations.json');expected=reassessment_profiles();images=[]
    check(set(metrics)==set(expected), 'Wrong result reassessment capture inventory')
    for name,(w,h,dpi,font,state) in expected.items():
        m=metrics[name];swiped=state=='swiped';drag=state=='mid-drag'
        check(m.get('viewport')==[w,h] and m.get('densityDpi')==dpi and type(m.get('fontScale')) in (int,float) and abs(m['fontScale']-font)<.0001, 'Wrong result viewport/density/font')
        check(m.get('resultPage')==(1 if swiped else 0) and m.get('selectedIndex')==(3 if swiped else 0), 'Swipe lost result page or selection')
        check(m.get('visibleSpecies')==(['S4','D1','I3'] if swiped else ['R13','R14','S3']) and m.get('visibleCardCount')==3, 'Swipe displayed wrong acquisition identities')
        check(m.get('mutationCount')==0 and m.get('dossierOpen') is False, 'Swipe wrote data or opened dossier')
        x=m.get('translationX');check(type(x) in (int,float) and math.isfinite(x) and (x<=-100*dpi/160 if drag else abs(x)<.01), 'Missing physical drag or final snap restoration')
        targets=m.get('refreshTargets');check(type(targets) is list and len(targets)==(0 if swiped else 1), 'Reassessment target eligibility inventory changed')
        for t in targets:
            b=t.get('bounds');check(type(b) is list and len(b)==4 and all(type(n) in (int,float) and math.isfinite(n) for n in b), 'Missing measured refresh bounds')
            check(t.get('tag')=='acquisition-reassess-card-0' and type(t.get('enabled')) is bool and type(t.get('description')) is str and t['description'].strip(), 'Missing refresh control identity/state/accessibility')
            check(all(type(t.get(k)) in (int,float) and math.isfinite(t[k]) and t[k]>=48 for k in ('widthDp','heightDp')), 'Refresh target below 48dp')
            check(abs((b[2]-b[0])*160/dpi-t['widthDp'])<1 and abs((b[3]-b[1])*160/dpi-t['heightDp'])<1, 'Refresh bounds disagree with measured target')
            if not drag: check(0<=b[0]<b[2]<=w and 0<=b[1]<b[3]<=h, 'Refresh target clipped at rest')
        images.append(verify_png(d/(name+'.png'),w,h,minimum_bytes=1000))
    return images

def verify_dossier_diagnostics(root):
    return [verify_png(root/'result-dossier'/f'780x360-dpi160-font{font}-results.png',780,360,minimum_bytes=1000) for font in (100,130)]

def verify_totals(full):
    check(full.get('status')=='passed' and full['totals']['tests']>=506 and full['totals']['passed']>=490 and full['totals']['skipped']<=16, 'Existing 401/385/16 baseline plus one hundred and five new tests required')

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--repo',type=Path,required=True)
    p.add_argument('--native-review',type=Path,required=True)
    p.add_argument('--test-results',type=Path,required=True)
    p.add_argument('--full-suite-report',type=Path,required=True)
    p.add_argument('--output',required=True)
    args=p.parse_args()
    from verify_registration_ci import verify_matrix_test, verify_receipt_test
    verify_matrix_test(args.repo)
    verify_receipt_test(args.repo)
    suites=junit(args.test_results)
    full=read(args.full_suite_report)
    verify_totals(full)
    images=verify_reassessment(args.native_review)+verify_replay(args.native_review)+verify_realtime_identity(args.native_review)+verify_battle_utilities(args.native_review)+verify_counter(args.native_review)+verify_motion(args.native_review)+verify_currency(args.native_review)+verify_team(args.native_review)+verify_fullscreen(args.native_review)+verify_insertion(args.native_review)+verify_story_cards(args.native_review)+verify_mode_navigation(args.native_review)
    for i in range(4): images.append(verify_png(args.native_review/'theme-state'/f'button-{i}-normal-pressed-cancelled-selected-disabled.png',1100,56,minimum_bytes=1000))
    evidence={'source_commit':git(args.repo,'rev-parse','HEAD').decode().strip(),
              'source_tree_sha256':fingerprint(args.repo,'HEAD'),'captures':images,'diagnostic_captures':verify_dossier_diagnostics(args.native_review),'required_methods':suites,'totals':full['totals'],
              'sidecar_sha256': {name:sha256((args.native_review/name).read_bytes()) for name in
                 ('result-reassessment/observations.json','battle-replay/manifest.json','battle-replay/metrics.json','battle-replay/findings.json','realtime-identity-revision/metrics.json','battle-ui-revision/observations.json','recruitment-counter/geometry.json','recruitment-counter/findings.json','recruitment-counter/claim-state.json','home-water-motion.json','currency-display/geometry.json','currency-display/findings.json','team-character-panels/manifest.json','team-character-panels/metrics.json','team-character-panels/findings.json','team-character-panels/inline-reorder.json','fullscreen/metrics.json','team-insertion-preview/party.json','team-insertion-preview/story-list.json','team-insertion-preview/story-order.json','story-cards/metrics.json','story-cards/findings.json','feedback-mode-navigation/manifest.json','feedback-mode-navigation/metrics.json','feedback-mode-navigation/findings.json')}}
    evidence['evidence_sha256']=sha256(json.dumps(evidence,sort_keys=True,separators=(',',':')).encode())
    emit(dict(status='passed',**evidence),args.output)

if __name__=='__main__': main()
